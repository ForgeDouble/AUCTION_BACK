package com.example.auction.admin.service;

import com.example.auction.admin.dto.AdminAuctionActionReq;
import com.example.auction.admin.dto.AdminAuctionRowDto;
import com.example.auction.bid.dto.BidEvent;
import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.BadRequestException;
import com.example.auction.common.exception.InternalErrorException;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.common.exception.UnauthorizedAccessException;
import com.example.auction.product.domain.Product;
import com.example.auction.product.domain.Status;
import com.example.auction.product.repository.ProductRepository;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Set;

@Service
@Slf4j
public class AdminAuctionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String KEY_AUCTION_START = "auction:start:";

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> bidPriceRedis;
    private final RedisTemplate<String, Object> bidRedis;

    public AdminAuctionService(
            ProductRepository productRepository,
            UserRepository userRepository, ObjectMapper objectMapper,
            @Qualifier("bidPrice") RedisTemplate<String, String> bidPriceRedis,
            @Qualifier("bid") RedisTemplate<String, Object> bidRedis
    ) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.bidPriceRedis = bidPriceRedis;
        this.bidRedis = bidRedis;
    }

    private String currentEmailOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank() || "anonymousUser".equals(auth.getName())) {
            throw new UnauthorizedAccessException("UNAUTHENTICATED", "로그인이 필요합니다.");
        }
        return auth.getName();
    }

    private User me() {
        String email = currentEmailOrThrow();
        return userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> {
                    log.warn("[INVALID_USER] email={}", email);
                    return new UnauthorizedAccessException("INVALID_USER", "유효하지 않은 유저입니다.");
                });
    }

    private User admin() {
        User user = me();
        if (user.getAuthority() != Authority.ADMIN && user.getAuthority() != Authority.INQUIRY) {
            log.warn("[UNAUTHORIZED_ACCESS] userId={} authority={}", user.getUserId(), user.getAuthority());
            throw new UnauthorizedAccessException("UNAUTHORIZED_ACCESS", "관리자 외 권한이 없습니다.");
        }
        return user;
    }

    // 상품 검색 관련(id 여부)
    private Product productOrThrow(long productId) {
        if (productId <= 0) {
            throw new BadRequestException("PRODUCT_ID_REQUIRED", "유효한 productId가 필요합니다.");
        }
        return productRepository.findById(productId)
                .filter(p -> p.getDelYn() == DelYN.N)
                .orElseThrow(() ->
                        new ResourceNotFoundException("PRODUCT_NOT_FOUND", "상품이 없습니다. productId=" + productId)
                );
    }

    @Transactional(readOnly = true)
    public Page<AdminAuctionRowDto> list(int page, int size) {
        admin();

        int p = Math.max(0, page);
        int s = Math.max(1, Math.min(size, 100));

        var productsPage = productRepository.findAdminMonitoring(PageRequest.of(p, s));

        return productsPage.map(product -> {
            Long pid = product.getProductId();

            String zKey = "product_bid_zset_" + pid;
            long bidCount = safeZCard(zKey);
            long currentBid = safeTopScore(zKey);

            if (currentBid <= 0) currentBid = (product.getPrice() == null ? 0L : product.getPrice());

            String sellerMasked = maskSeller(product);
            String category = (product.getCategory() == null) ? "미분류" : product.getCategory().getCategoryName();

            String endsAtIso = null;
            if (product.getAuctionEndTime() != null) {
                endsAtIso = product.getAuctionEndTime().atZone(KST).toInstant().toString();
            }

            String status = Boolean.TRUE.equals(product.getBlocked())
                    ? "BLOCKED"
                    : (product.getStatus() == null ? "UNKNOWN" : product.getStatus().name());

            return new AdminAuctionRowDto(
                    String.valueOf(pid),
                    product.getProductName(),
                    sellerMasked,
                    category,
                    currentBid,
                    bidCount,
                    endsAtIso,
                    status
            );
        });
    }

    @Transactional
    public void suspend(long productId, AdminAuctionActionReq adminAuctionActionReq) {
        admin();

        Product product = productOrThrow(productId);

        String reason = "";
        if (adminAuctionActionReq != null && adminAuctionActionReq.reason() != null && !adminAuctionActionReq.reason().isBlank()) {
            reason = adminAuctionActionReq.reason().trim();
        }

        try {
            product.block(reason);
            productRepository.save(product);
            log.info("[ADMIN_AUCTION_SUSPEND] productId={} reason={}", productId, reason);
        } catch (RuntimeException e) {
            log.error("[ADMIN_AUCTION_SUSPEND_FAILED] productId={}", productId, e);
            throw new InternalErrorException("ADMIN_AUCTION_SUSPEND_FAILED", "상품 차단 처리 중 오류가 발생했습니다.");
        }
    }

    @Transactional
    public void forceEnd(long productId, AdminAuctionActionReq req) {
        admin();

        Product product = productOrThrow(productId);

        try {
            if (product.getStatus() == Status.SELLED || product.getStatus() == Status.NOTSELLED) {
                cleanupRedisKeys(productId);
                return;
            }

            // Redis -> 최고 입찰/입찰수 -> 낙찰 여부 결정
            String bidZSetKey = "product_bid_zset_" + productId;
            String bidHashKey = "product_bid_hash_" + productId;

            Long zcountObj = bidPriceRedis.opsForZSet().size(bidZSetKey);
            long zcount = (zcountObj == null ? 0L : zcountObj);

            Set<String> winners = bidPriceRedis.opsForZSet().reverseRange(bidZSetKey, 0, 0);

            BidEvent winnerBid = null;
            String winnerUuid = null;

            if (winners != null && !winners.isEmpty()) {
                winnerUuid = winners.iterator().next();
                Object raw = bidPriceRedis.opsForHash().get(bidHashKey, winnerUuid);
                if (raw != null) {
                    try {
                        winnerBid = objectMapper.readValue(raw.toString(), BidEvent.class);
                    } catch (Exception ex) {
                        log.warn("[ADMIN_AUCTION_WINNER_PARSE_FAIL] productId={} uuid={}", productId, winnerUuid, ex);
                    }
                }
            }

            boolean hasRealWinner = (zcount >= 2) && (winnerBid != null) && (winnerBid.getUserId() != null);

            if (hasRealWinner) {
                product.updateStatus(Status.SELLED);
                log.info("[ADMIN_AUCTION_FORCE_END] productId={} result=SELLED winnerUserId={} amount={}",
                        productId, winnerBid.getUserId(), winnerBid.getBidAmount());
            } else {
                product.updateStatus(Status.NOTSELLED);
                log.info("[ADMIN_AUCTION_FORCE_END] productId={} result=NOTSELLED zcount={}", productId, zcount);
            }

            productRepository.save(product);
            cleanupRedisKeys(productId);

        } catch (BadRequestException | ResourceNotFoundException | UnauthorizedAccessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("[ADMIN_AUCTION_FORCE_END_FAILED] productId={}", productId, e);
            throw new InternalErrorException("ADMIN_AUCTION_FORCE_END_FAILED", "경매 강제 종료 처리 중 오류가 발생했습니다.");
        }
    }

    private long safeZCard(String zKey) {
        try {
            Long v = bidPriceRedis.opsForZSet().zCard(zKey);
            return v == null ? 0L : v;
        } catch (Exception e) {
            log.warn("[REDIS_ZCARD_FAIL] key={}", zKey, e);
            return 0L;
        }
    }

    private long safeTopScore(String zKey) {
        try {
            ZSetOperations<String, String> zSetOperations = bidPriceRedis.opsForZSet();
            Set<ZSetOperations.TypedTuple<String>> top = zSetOperations.reverseRangeWithScores(zKey, 0, 0);
            if (top == null || top.isEmpty()) return 0L;
            Double score = top.iterator().next().getScore();
            return score == null ? 0L : score.longValue();
        } catch (Exception e) {
            log.warn("[REDIS_TOP_SCORE_FAIL] key={}", zKey, e);
            return 0L;
        }
    }

    private void cleanupRedisKeys(long productId) {
        try {
            bidPriceRedis.delete("product_bid_zset_" + productId);
            bidPriceRedis.delete("product_bid_hash_" + productId);
            bidPriceRedis.delete("auction_end_time_" + productId);
            bidRedis.delete(KEY_AUCTION_START + productId);
        } catch (Exception e) {
            log.warn("[ADMIN_AUCTION_REDIS_CLEANUP_FAIL] productId={}", productId, e);
        }
    }

    private String maskSeller(Product product) {
        String nick = (product.getUser() != null && product.getUser().getNickname() != null)
                ? product.getUser().getNickname()
                : (product.getUser() != null ? product.getUser().getEmail() : "seller");

        if (nick == null || nick.isBlank()) return "seller";
        if (nick.length() <= 2) return nick.charAt(0) + "*";
        if (nick.length() == 3) return nick.charAt(0) + "*" + nick.charAt(2);
        return nick.substring(0, 2) + "***";
    }
}
