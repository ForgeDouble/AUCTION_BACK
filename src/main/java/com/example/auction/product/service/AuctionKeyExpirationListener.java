package com.example.auction.product.service;

import com.example.auction.notification.service.AuctionNotificationService;
import com.example.auction.product.domain.Product;
import com.example.auction.product.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Redis 키 만료 이벤트 리스너
 * - auction:start:{pid} → 경매 시작
 * - auction:end:{pid} → 경매 종료
 * - auction:notify10:{pid} → 10분 전 알림
 * - auction:notify5:{pid} → 5분 전 알림
 */
@Component
@Slf4j
public class AuctionKeyExpirationListener implements MessageListener {

    private final ProductService productService;
    private final AuctionNotificationService notificationService;
    private final RedisTemplate<String, String> bidRedisTemplate;

    // 키 prefix 상수
    private static final String KEY_START = "auction:start:";
    private static final String KEY_END = "auction:end:";
    private static final String KEY_NOTIFY_10 = "auction:notify10:";
    private static final String KEY_NOTIFY_5 = "auction:notify5:";

    // 락 키 prefix
    private static final String LOCK_START = "lock:start:";
    private static final String LOCK_END = "lock:end:";
    private static final String LOCK_NOTIFY_10 = "lock:notify10:";
    private static final String LOCK_NOTIFY_5 = "lock:notify5:";

    private static final long LOCK_TTL_SECONDS = 10L;

    public AuctionKeyExpirationListener(
            ProductService productService,
            AuctionNotificationService notificationService,
            @Qualifier("bidPrice") RedisTemplate<String, String> bidRedisTemplate) {
        this.productService = productService;
        this.notificationService = notificationService;
        this.bidRedisTemplate = bidRedisTemplate;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString();
        log.debug("[RedisExpired] 키 만료 감지: {}", expiredKey);

        try {
            // 경매 시작 이벤트
            if (expiredKey.startsWith(KEY_START)) {
                long pid = extractProductId(expiredKey, KEY_START);
                handleAuctionStart(pid);
            }
            // 경매 종료 이벤트
            else if (expiredKey.startsWith(KEY_END)) {
                long pid = extractProductId(expiredKey, KEY_END);
                handleAuctionEnd(pid);
            }
            // 10분 전 알림
            else if (expiredKey.startsWith(KEY_NOTIFY_10)) {
                long pid = extractProductId(expiredKey, KEY_NOTIFY_10);
                handleNotification(pid, 10, LOCK_NOTIFY_10);
            }
            // 5분 전 알림
            else if (expiredKey.startsWith(KEY_NOTIFY_5)) {
                long pid = extractProductId(expiredKey, KEY_NOTIFY_5);
                handleNotification(pid, 5, LOCK_NOTIFY_5);
            }
        } catch (NumberFormatException e) {
            log.error("[RedisExpired] Product ID 파싱 실패: {}", expiredKey, e);
        } catch (Exception e) {
            log.error("[RedisExpired] 이벤트 처리 실패: {}", expiredKey, e);
        }
    }

    /**
     * 경매 시작 처리
     */
    private void handleAuctionStart(long pid) {
        String lockKey = LOCK_START + pid;
        Boolean locked = bidRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(LOCK_TTL_SECONDS));

        if (!Boolean.TRUE.equals(locked)) {
            log.debug("[AuctionStart] 이미 처리 중 pid={}", pid);
            return;
        }

        try {
            log.info("[AuctionStart] 경매 시작 pid={}", pid);

            // 경매 시작 (READY → PROCESSING)
            productService.startAuction(pid);

            // 상품 정보 조회
            Product product = productService.getProduct(pid);
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime endTime = product.getAuctionEndTime();

            // 종료까지 남은 시간 계산
            long secondsUntilEnd = Duration.between(now, endTime).getSeconds();
            log.info("[secondsUntilEnd] 종료까지 남은 시간={} ", secondsUntilEnd);

            if (secondsUntilEnd > 0) {
                // 경매 종료 타이머 등록
                bidRedisTemplate.opsForValue().set(
                        KEY_END + pid,
                        "1",
                        Duration.ofSeconds(secondsUntilEnd)
                );
                log.info("[AuctionStart] 종료 타이머 등록 pid={}, seconds={}", pid, secondsUntilEnd);

                // 10분 전 알림 타이머 (종료 10분 전 = 남은시간 - 600초)
                if (secondsUntilEnd > 600) {
                    bidRedisTemplate.opsForValue().set(
                            KEY_NOTIFY_10 + pid,
                            "1",
                            Duration.ofSeconds(secondsUntilEnd - 600)
                    );
                    log.info("[AuctionStart] 10분 전 알림 타이머 등록 pid={}", pid);
                }

                // 5분 전 알림 타이머 (종료 5분 전 = 남은시간 - 300초)
                if (secondsUntilEnd > 300) {
                    bidRedisTemplate.opsForValue().set(
                            KEY_NOTIFY_5 + pid,
                            "1",
                            Duration.ofSeconds(secondsUntilEnd - 300)
                    );
                    log.info("[AuctionStart] 5분 전 알림 타이머 등록 pid={}", pid);
                }
            } else {
                log.warn("[AuctionStart] 이미 종료 시간이 지남 pid={}", pid);
            }

        } catch (Exception e) {
            log.error("[AuctionStart] 경매 시작 실패 pid={}", pid, e);
        }
    }

    /**
     * 경매 종료 처리
     */
    private void handleAuctionEnd(long pid) {
        String lockKey = LOCK_END + pid;
        Boolean locked = bidRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(LOCK_TTL_SECONDS));

        if (!Boolean.TRUE.equals(locked)) {
            log.debug("[AuctionEnd] 이미 처리 중 pid={}", pid);
            return;
        }

        try {
            log.info("[AuctionEnd] 경매 종료 pid={}", pid);

            // 상품 조회 및 종료 처리 (PROCESSING → SELLED/FAILED)
            Product product = productService.getProduct(pid);
            productService.endAuction(product);

            // Redis 입찰 데이터 정리
            cleanupAuctionData(pid);

        } catch (Exception e) {
            log.error("[AuctionEnd] 경매 종료 실패 pid={}", pid, e);
        }
    }

    /**
     * 알림 처리
     */
    private void handleNotification(long pid, int minutes, String lockPrefix) {
        String lockKey = lockPrefix + pid;
        Boolean locked = bidRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(LOCK_TTL_SECONDS));

        if (!Boolean.TRUE.equals(locked)) {
            log.debug("[Notify] 이미 처리 중 pid={}, minutes={}", pid, minutes);
            return;
        }

        try {
            log.info("[Notify] {}분 전 알림 발송 pid={}", minutes, pid);
//            미구현
//            notificationService.notifyEndingSoon(pid, minutes);
        } catch (Exception e) {
            log.error("[Notify] {}분 전 알림 실패 pid={}", minutes, pid, e);
        }
    }

    /**
     * Redis 경매 데이터 정리
     */
    private void cleanupAuctionData(long pid) {
        try {
            bidRedisTemplate.delete("product_bid_zset_" + pid);
            bidRedisTemplate.delete("product_bid_hash_" + pid);
            bidRedisTemplate.delete("auction_end_time_" + pid);
            log.debug("[Cleanup] Redis 데이터 정리 완료 pid={}", pid);
        } catch (Exception e) {
            log.warn("[Cleanup] Redis 정리 실패 pid={}", pid, e);
        }
    }

    /**
     * 키에서 Product ID 추출
     */
    private long extractProductId(String key, String prefix) {
        return Long.parseLong(key.substring(prefix.length()));
    }
}