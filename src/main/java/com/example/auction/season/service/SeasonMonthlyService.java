package com.example.auction.season.service;

import com.example.auction.bid.repository.BidRepository;
import com.example.auction.common.exception.BadRequestException;
import com.example.auction.common.exception.InternalErrorException;
import com.example.auction.product.repository.ProductRepository;
import com.example.auction.review.repository.ReviewRepository;
import com.example.auction.season.domain.MonthlyBadgeAward;
import com.example.auction.season.domain.MonthlyTitleAward;
import com.example.auction.season.domain.SeasonBadgeType;
import com.example.auction.season.domain.SeasonTitleType;
import com.example.auction.season.dto.MonthlyBadgeAwardCreateDto;
import com.example.auction.season.dto.MonthlyTitleAwardCreateDto;
import com.example.auction.season.repository.MonthlyBadgeAwardRepository;
import com.example.auction.season.repository.MonthlyTitleAwardRepository;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.example.auction.season.domain.SeasonRules.*;


@Service
@Slf4j
@RequiredArgsConstructor
public class SeasonMonthlyService {

    private final ProductRepository productRepository;
    private final BidRepository bidRepository;
    private final ReviewRepository reviewRepository;

    private final MonthlyTitleAwardRepository monthlyTitleAwardRepository;
    private final MonthlyBadgeAwardRepository monthlyBadgeAwardRepository;

    private final UserRepository userRepository;
    private final RedissonClient redissonClient;

    private String ymString(YearMonth ym) {
        return ym.toString();
    }

    private LocalDateTime startOf(YearMonth ym) {
        return ym.atDay(1).atStartOfDay();
    }

    private LocalDateTime endOf(YearMonth ym) {
        return ym.plusMonths(1).atDay(1).atStartOfDay();
    }

    private boolean eligible(User u) {
        if (u == null) return false;
        if (!AWARD_ONLY_USER_AUTHORITY) return true;
        return u.getAuthority() == Authority.USER;
    }

    @Transactional
    public void runForMonth(YearMonth ym, boolean overwrite) {
        if (ym == null) {
            throw new BadRequestException("YM_REQUIRED", "ym(YearMonth)값 이 필요합니다.");
        }

        String ymStr = ymString(ym);
        String lockKey = "lock:season:monthly:" + ymStr;

        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired;
            try {
                acquired = lock.tryLock(0, 10, TimeUnit.MINUTES);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("[SEASON_LOCK_INTERRUPTED] ym={} key={}", ymStr, lockKey, ie);
                throw new InternalErrorException("SEASON_LOCK_INTERRUPTED", "시즌 집계 락 대기 중 인터럽트가 발생했습니다.");
            }

            if (!acquired) {
                log.warn("[SEASON_LOCK_NOT_ACQUIRED] ym={} key={}", ymStr, lockKey);
                return;
            }

            if (overwrite) {
                monthlyTitleAwardRepository.deleteByYm(ymStr);
                monthlyBadgeAwardRepository.deleteByYm(ymStr);
            }

            LocalDateTime start = startOf(ym);
            LocalDateTime end = endOf(ym);

            Map<Long, User> userMap = new HashMap<>();

            saveTitleAwards(ymStr, start, end, userMap);
            saveBadgeAwards(ymStr, start, end, userMap);

            log.info("[Season] 월간 시즌 집계 완료 ym={}", ymStr);

        } catch (BadRequestException e) {
            throw e;
        } catch (InternalErrorException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("[SEASON_MONTHLY_RUNTIME_ERROR] ym={}", ymStr, e);
            throw new InternalErrorException("SEASON_MONTHLY_FAILED", "월간 시즌 집계 중 오류가 발생했습니다.");
        } finally {
            try {
                if (lock.isHeldByCurrentThread()) lock.unlock();
            } catch (Exception unlockEx) {
                log.warn("[SEASON_LOCK_UNLOCK_FAIL] ym={} key={}", ymStr, lockKey, unlockEx);
            }
        }
    }

    private void warmUsers(Collection<Long> userIds, Map<Long, User> userMap) {
        if (userIds == null || userIds.isEmpty()) return;

        List<Long> missing = userIds.stream()
                .filter(id -> !userMap.containsKey(id))
                .toList();

        if (missing.isEmpty()) return;

        List<User> users = userRepository.findAllById(missing);
        for (User user : users) {
            if (user == null) continue;
            userMap.put(user.getUserId(), user);
        }
    }

    private void saveTitleAwards(String ymStr, LocalDateTime start, LocalDateTime end, Map<Long, User> userMap) {

        // 판매왕 (판매 완료 건수)
        var sellCountRows = productRepository.countSoldBySellerBetween(start, end);
        persistTopNTitle(ymStr, SeasonTitleType.SELL_COUNT_KING, sellCountRows, userMap,
                MIN_SELL_COUNT_FOR_KING, true);

        // 매출왕 (판매 GMV 합 = 낙찰가 합)
        var sellGmvRows = productRepository.sumSoldGmvBySellerBetween(start, end);
        persistTopNTitle(ymStr, SeasonTitleType.SELL_GMV_KING, sellGmvRows, userMap,
                MIN_SELL_GMV_FOR_KING, true);

        // 구매왕 (낙찰 횟수)
        var buyCountRows = bidRepository.countWinningByBuyerBetween(start, end);
        persistTopNTitle(ymStr, SeasonTitleType.BUY_COUNT_KING, buyCountRows, userMap,
                MIN_BUY_COUNT_FOR_KING, true);

        // 큰손왕 (낙찰 GMV 합)
        var buyGmvRows = bidRepository.sumWinningAmountByBuyerBetween(start, end);
        persistTopNTitle(ymStr, SeasonTitleType.BUY_GMV_KING, buyGmvRows, userMap,
                1, false);

        // 경매왕 (그 달 입찰 참여 상품 수)
        var auctionRows = bidRepository.countDistinctProductsBidBetween(start, end);
        persistTopNTitle(ymStr, SeasonTitleType.AUCTION_KING, auctionRows, userMap,
                MIN_AUCTION_PARTICIPATION_FOR_KING, true);

        // 저격왕
        persistSniperKing(ymStr, start, end, userMap);
    }

    private void persistTopNTitle(
            String ymStr,
            SeasonTitleType type,
            List<? extends Object> rows,
            Map<Long, User> userMap,
            long minValue,
            boolean requireMinForRank1
    ) {
        List<SimpleRow> list = new ArrayList<>();
        for (Object object : rows) {
            if (object == null) continue;

            Long uid;
            Long v;
            try {
                uid = (Long) object.getClass().getMethod("getUserId").invoke(object);
                v = (Long) object.getClass().getMethod("getV").invoke(object);
            } catch (Exception ex) {
                log.warn("[SEASON_ROW_PARSE_FAIL] type={} rowClass={}", type,
                        object.getClass().getName(), ex);
                continue;
            }

            if (uid == null || v == null) continue;
            if (v < 0) continue;
            list.add(new SimpleRow(uid, v));
        }
        if (list.isEmpty()) return;

        list.sort((a, b) -> Long.compare(b.v, a.v));

        if (requireMinForRank1) {
            SimpleRow top1 = list.get(0);
            if (top1.v < minValue) return;
        }

        List<SimpleRow> top = list.stream().limit(TOP_N).toList();
        warmUsers(top.stream().map(x -> x.userId).toList(), userMap);

        List<MonthlyTitleAward> awards = new ArrayList<>();
        int rank = 1;
        for (SimpleRow row : top) {
            User user = userMap.get(row.userId);
            if (!eligible(user)) continue;

            var createDto = MonthlyTitleAwardCreateDto.of(ymStr, type, row.userId, user, rank++, row.v, null);
            awards.add(createDto.toEntity());

        }
        if (!awards.isEmpty()) monthlyTitleAwardRepository.saveAll(awards);
    }

    private void persistSniperKing(String ymStr, LocalDateTime start, LocalDateTime end, Map<Long, User> userMap) {

        // 낙찰 건 whghl
        Map<Long, Long> wins = bidRepository.countWinningByBuyerBetween(start, end).stream()
                .collect(Collectors.toMap(
                        BidRepository.UserLongRow::getUserId,
                        BidRepository.UserLongRow::getV,
                        (a, b) -> a
                ));

        Map<Long, Long> denom = bidRepository.countDistinctEndedSoldProductsParticipatedBetween(start, end).stream()
                .collect(Collectors.toMap(
                        BidRepository.UserLongRow::getUserId,
                        BidRepository.UserLongRow::getV,
                        (a, b) -> a
                ));

        List<SniperRow> candidates = new ArrayList<>();
        for (var e : denom.entrySet()) {
            Long userId = e.getKey();
            long participated = e.getValue() == null ? 0 : e.getValue();
            long win = wins.getOrDefault(userId, 0L);

            if (participated < MIN_AUCTION_PARTICIPATION_FOR_KING) continue;
            if (win < SNIPER_MIN_WINS) continue;

            double rate = (participated == 0) ? 0.0 : (double) win / (double) participated;

            candidates.add(new SniperRow(userId, participated, win, rate));
        }

        candidates.sort((a, b) -> {
            int c = Double.compare(b.rate, a.rate);
            if (c != 0) return c;
            c = Long.compare(b.win, a.win);
            if (c != 0) return c;
            return Long.compare(b.participated, a.participated);
        });

        List<SniperRow> top = candidates.stream().limit(TOP_N).toList();
        if (top.isEmpty()) return;

        warmUsers(top.stream().map(x -> x.userId).toList(), userMap);

        List<MonthlyTitleAward> awards = new ArrayList<>();
        int rank = 1;
        for (SniperRow r : top) {
            User u = userMap.get(r.userId);
            if (!eligible(u)) continue;

            var createDto = MonthlyTitleAwardCreateDto.of(ymStr, SeasonTitleType.SNIPER_KING, r.userId, u, rank++, r.win, r.rate);
            awards.add(createDto.toEntity());
        }
        if (!awards.isEmpty()) monthlyTitleAwardRepository.saveAll(awards);
    }

    private void saveBadgeAwards(String ymStr, LocalDateTime start, LocalDateTime end, Map<Long, User> userMap) {

        // 판매자별 총 리뷰 수
        Map<Long, Long> totalMap = reviewRepository.countMonthlyReviewsBySeller(start, end).stream()
                .collect(Collectors.toMap(
                        ReviewRepository.SellerCountRow::getSellerId,
                        ReviewRepository.SellerCountRow::getCnt,
                        (a, b) -> a
                ));

        for (SeasonBadgeType badgeType : SeasonBadgeType.values()) {
            Map<Long, Long> tagMap = reviewRepository.countMonthlyTagBySeller(start, end, badgeType.getReviewTag())
                    .stream()
                    .collect(Collectors.toMap(
                            ReviewRepository.SellerCountRow::getSellerId,
                            ReviewRepository.SellerCountRow::getCnt,
                            (a, b) -> a
                    ));

            List<BadgeRow> rows = new ArrayList<>();
            for (var e : totalMap.entrySet()) {
                Long sellerId = e.getKey();
                long total = e.getValue() == null ? 0 : e.getValue();
                long tagCnt = tagMap.getOrDefault(sellerId, 0L);

                if (total < MIN_REVIEWS_FOR_BADGE) continue;
                if (tagCnt < MIN_TAG_COUNT_FOR_BADGE) continue;

                double ratio = (total == 0) ? 0.0 : (double) tagCnt / (double) total;
                if (ratio < MIN_TAG_RATIO_FOR_BADGE) continue;

                rows.add(new BadgeRow(sellerId, tagCnt, total, ratio));
            }

            rows.sort((a, b) -> {
                int c = Double.compare(b.ratio, a.ratio);
                if (c != 0) return c;
                c = Long.compare(b.totalReviews, a.totalReviews);
                if (c != 0) return c;
                return Long.compare(b.tagCount, a.tagCount);
            });

            List<BadgeRow> top = rows.stream().limit(TOP_N).toList();
            if (top.isEmpty()) continue;

            warmUsers(top.stream().map(x -> x.userId).toList(), userMap);

            List<MonthlyBadgeAward> awards = new ArrayList<>();
            int rank = 1;
            for (BadgeRow r : top) {
                User u = userMap.get(r.userId);
                if (!eligible(u)) continue;

                var createDto = MonthlyBadgeAwardCreateDto.of(ymStr, badgeType, r.userId, u, rank++, r.tagCount, r.totalReviews, r.ratio);
                awards.add(createDto.toEntity());
            }
            if (!awards.isEmpty()) monthlyBadgeAwardRepository.saveAll(awards);
        }
    }

    private record SimpleRow(Long userId, Long v) {}
    private record SniperRow(Long userId, Long participated, Long win, Double rate) {}
    private record BadgeRow(Long userId, Long tagCount, Long totalReviews, Double ratio) {}
}
