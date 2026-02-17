package com.example.auction.admin.service;

import java.time.format.DateTimeFormatter;
import java.util.*;

import com.example.auction.admin.dto.AdminAuctionTrendRowDto;
import com.example.auction.admin.dto.AdminMonthlyTradeRowDto;
import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.InternalErrorException;
import com.example.auction.common.exception.UnauthorizedAccessException;
import com.example.auction.product.domain.Status;
import com.example.auction.product.repository.ProductRepository;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.auction.admin.dto.ActiveHourBucketDto;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;



import org.springframework.beans.factory.annotation.Qualifier;

@Service
@Slf4j
public class AdminMetricsService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate metricsRedis;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public AdminMetricsService(@Qualifier("metrics") StringRedisTemplate metricsRedis, ProductRepository productRepository, UserRepository userRepository) {
        this.metricsRedis = metricsRedis;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    private String currentEmailOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank() || "anonymousUser".equals(auth.getName())) {
            throw new UnauthorizedAccessException("UNAUTHENTICATED", "로그인이 필요합니다.");
        }
        return auth.getName();
    }

    private User admin() {
        String email = currentEmailOrThrow();
        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new UnauthorizedAccessException("INVALID_USER", "유효하지 않은 유저입니다."));
        if (user.getAuthority() != Authority.ADMIN && user.getAuthority() != Authority.INQUIRY) {
            throw new UnauthorizedAccessException("UNAUTHORIZED_ACCESS", "관리자 외 권한이 없습니다.");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public List<ActiveHourBucketDto> getTodayActiveUsers3h() {
        admin();

        String day = LocalDate.now(KST).format(DATE);

        int[] hours = new int[] {0, 3, 6, 9, 12, 15, 18, 21};
        List<ActiveHourBucketDto> out = new ArrayList<>(hours.length);

        for (int h : hours) {
            String hh = String.format("%02d", h);
            String key = "metrics:activeUsers3h:" + day + ":" + hh;

            long size = safeSetSize(key);

            out.add(ActiveHourBucketDto.builder()
                    .hour(hh)
                    .count(size)
                    .build());
        }

        return out;
    }

    private long safeSetSize(String key) {
        try {
            Long v = metricsRedis.opsForSet().size(key);
            return (v == null ? 0L : v);
        } catch (Exception e) {
            log.warn("[METRICS_REDIS_SET_SIZE_FAIL] key={}", key, e);
            return 0L;
        }
    }

    // 최근 7일 내 생성/종료 확인용 (그래프 지표)
    @Transactional(readOnly = true)
    public List<AdminAuctionTrendRowDto> auctionTrend(int days) {
        admin();

        int day = Math.max(1, Math.min(days, 30));

        LocalDate today = LocalDate.now(KST);
        LocalDate startDate = today.minusDays(day - 1);
        LocalDate endExclusiveDate = today.plusDays(1);

        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to = endExclusiveDate.atStartOfDay();
        try {
            Map<LocalDate, Long> createdMap = toMap(
                    productRepository.countCreatedDaily(from, to));
            Map<LocalDate, Long> endedMap = toMap(
                    productRepository.countEndedDaily(from, to, List.of(Status.SELLED, Status.NOTSELLED)));

            List<AdminAuctionTrendRowDto> out = new ArrayList<>(day);
            for (int i = 0; i < day; i++) {
                LocalDate date = startDate.plusDays(i);
                long created = createdMap.getOrDefault(date, 0L);
                long ended = endedMap.getOrDefault(date, 0L);
                out.add(new AdminAuctionTrendRowDto(date.toString(), created, ended));
            }
            return out;
        } catch (RuntimeException e) {
            log.error("[ADMIN_METRICS_AUCTION_TREND_FAILED] days={} from={} to={}", day, from, to, e);
            throw new InternalErrorException("ADMIN_METRICS_AUCTION_TREND_FAILED", "경매 트렌드 지표 조회 중 오류가 발생했습니다.");
        }
    }

    private Map<LocalDate, Long> toMap(List<ProductRepository.DayCountRow> rows) {
        Map<LocalDate, Long> map = new HashMap<>();
        if (rows == null || rows.isEmpty()) return map;

        for (var r : rows) {
            Date date = (Date) r.getD();
            if (date == null) continue;
            LocalDate ld = date.toLocalDate();
            long cnt = (r.getCnt() == null ? 0L : r.getCnt());
            map.put(ld, cnt);
        }
        return map;
    }

    // 월별 거래 금액 추이 확인 지표
    @Transactional(readOnly = true)
    public List<AdminMonthlyTradeRowDto> monthlyTrade(int months) {
        admin();

        int m = Math.max(1, Math.min(months, 24));

        LocalDate today = LocalDate.now(KST);

        // 조회 시작 끝 달 확인용
        LocalDate startMonth = today.withDayOfMonth(1).minusMonths(m - 1);
        LocalDate endExclusiveMonth = today.withDayOfMonth(1).plusMonths(1);

        LocalDateTime from = startMonth.atStartOfDay();
        LocalDateTime to = endExclusiveMonth.atStartOfDay();

        try {
            var rows = productRepository.sumMonthlyTradeAmountSold(from, to);

            Map<String, Long> map = new HashMap<>();
            if (rows != null) {
                for (var r : rows) {
                    if (r == null) continue;
                    String ym = r.getYm();
                    if (ym == null || ym.isBlank()) continue;

                    long amount = (r.getAmount() == null ? 0L : r.getAmount());
                    map.put(ym, amount);
                }
            }

            List<AdminMonthlyTradeRowDto> out = new ArrayList<>(m);
            for (int i = 0; i < m; i++) {
                LocalDate month = startMonth.plusMonths(i);
                String ym = month.getYear() + "-" + String.format("%02d", month.getMonthValue());
                out.add(new AdminMonthlyTradeRowDto(ym, map.getOrDefault(ym, 0L)));
            }
            return out;

        } catch (RuntimeException e) {
            log.error("[ADMIN_METRICS_MONTHLY_TRADE_FAILED] months={} from={} to={}", m, from, to, e);
            throw new InternalErrorException("ADMIN_METRICS_MONTHLY_TRADE_FAILED", "월별 거래 지표 조회 중 오류가 발생했습니다.");
        }
    }

}
