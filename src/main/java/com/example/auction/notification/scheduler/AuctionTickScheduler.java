package com.example.auction.notification.scheduler;

import com.example.auction.common.domain.DelYN;
import com.example.auction.notification.service.AuctionNotificationService;
import com.example.auction.product.domain.Product;
import com.example.auction.product.domain.Status;
import com.example.auction.product.repository.ProductRepository;
import com.example.auction.product.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.List;
import java.util.Set;

@Component
@Slf4j
public class AuctionTickScheduler {

    private final ProductRepository productRepository;
    private final ProductService productService;
    private final AuctionNotificationService auctionNotificationService;
    @Qualifier("bidPrice")
    private final RedisTemplate<String, String> bidRedisTemplate;

    private static final String Z_TO_START   = "auction:toStart";
    private static final String Z_ACTIVE     = "auction:active";
    private static final String Z_NOTIFY_10  = "auction:notify:10";
    private static final String Z_NOTIFY_5   = "auction:notify:5";

    public AuctionTickScheduler(ProductRepository productRepository, ProductService productService, AuctionNotificationService auctionNotificationService, @Qualifier("bidPrice") RedisTemplate<String, String> bidRedisTemplate) {
        this.productRepository = productRepository;
        this.productService = productService;
        this.auctionNotificationService = auctionNotificationService;
        this.bidRedisTemplate = bidRedisTemplate;
    }

    private static String kEndTime(long pid) { return "auction_end_time_" + pid; }

    private static String lockStart(long pid) { return "lock:auction:start:" + pid; }
    private static String lockEnd(long pid) { return "lock:auction:end:" + pid; }
    private static String lockN10(long pid) { return "lock:auction:n10:" + pid; }
    private static String lockN5(long pid) { return "lock:auction:n5:" + pid; }

    // 1회 처리 최대
    private static final int CHUNK_SIZE = 200;
    // 작업 락 TTL
    private static final long LOCK_SEC = 30;
    // toStart 인덱싱 -> 생성 후 ~120분
    private static final long INDEX_WINDOW_MIN = 120;

    private static long nowMs() {
        return System.currentTimeMillis();
    }
    private static long toEpochMs(LocalDateTime localDateTime) {
        return localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    // Repair / Index: 최근 생성 상품을 toStart ZSET에 인덱싱
//    @Scheduled(fixedDelay = 60_000L, initialDelay = 20_000L)
//    public void repairToStartIndex() {
//        LocalDateTime localDateTime = LocalDateTime.now();
//        LocalDateTime createdAfter = localDateTime.minusMinutes(INDEX_WINDOW_MIN);
//        List<Product> recent = productRepository.findByStatusAndDelYnAndBlockedAndCreatedAtAfter(
//                Status.PROCESSING, DelYN.N, false, createdAfter
//        );
//        if (recent.isEmpty()) return;
//
//        int added = 0;
//        for (Product product : recent) {
//            LocalDateTime start = product.getAuctionStartTime();
//            LocalDateTime end = product.getAuctionEndTime();
//            if (localDateTime.isAfter(end)) continue;
//            // toStart에 등록
//            bidRedisTemplate.opsForZSet().add(Z_TO_START, String.valueOf(product.getProductId()), toEpochMs(start));
//            added++;
//        }
//        if (added > 0) log.debug("[AuctionIndex] toStart 업데이트 + 수정 count={}", added);
//    }
//
//    // start tick 관련 -> toStart 을 통한 now 도달 피드 확인 -> startAuction -> active 인덱싱
//    @Scheduled(fixedDelay = 5_000L, initialDelay = 30_000L)
//    public void startTick() {
//        long now = nowMs();
//        // due set 가져오기 (LIMIT)
//        Set<String> due = bidRedisTemplate.opsForZSet().rangeByScore(Z_TO_START, 0, now, 0, CHUNK_SIZE);
//        if (due == null || due.isEmpty()) return;
//
//        for (String pidStr : due) {
//            long pid = Long.parseLong(pidStr);
//            // lock -> 분산 중복 방지
//            Boolean ok = bidRedisTemplate.opsForValue().setIfAbsent(lockStart(pid), "1", Duration.ofSeconds(LOCK_SEC));
//            if (!Boolean.TRUE.equals(ok)) continue;
//
//            try {
//                productService.startAuction(pid);
//
//                // baseline in 종료시각 Redis 키
//                String endEpochStr = bidRedisTemplate.opsForValue().get(kEndTime(pid));
//                long endEpochMs;
//                if (endEpochStr != null && !endEpochStr.isBlank()) {
//                    endEpochMs = Long.parseLong(endEpochStr);
//                } else {
//                    Product product = productRepository.findById(pid).orElse(null);
//                    if (product == null) {
//                        bidRedisTemplate.opsForZSet().remove(Z_TO_START, pidStr);
//                        continue;
//                    }
//                    endEpochMs = toEpochMs(product.getAuctionEndTime());
//                }
//
//                // active/notify 인덱싱 관련 코드
//                bidRedisTemplate.opsForZSet().add(Z_ACTIVE, pidStr, endEpochMs);
//                bidRedisTemplate.opsForZSet().add(Z_NOTIFY_10, pidStr, endEpochMs - 10 * 60_000L);
//                bidRedisTemplate.opsForZSet().add(Z_NOTIFY_5,  pidStr, endEpochMs -  5 * 60_000L);
//
//                bidRedisTemplate.opsForZSet().remove(Z_TO_START, pidStr);
//            } catch (Exception e) {
//                log.warn("[AuctionTick] startAuction 실패 pid={}", pid, e);
//            }
//        }
//    }



    // 종료 틱 관련 -> 만기 pid 드레인하여 성공 / 실패 무관 active 에서 제거 + lock 을 통한 중복 방지
    @Scheduled(fixedDelay = 5_000L, initialDelay = 35_000L)
    public void endTick() {
        long now = nowMs();
        Set<String> expired = bidRedisTemplate.opsForZSet().rangeByScore(Z_ACTIVE, 0, now, 0, CHUNK_SIZE);
        if (expired == null || expired.isEmpty()) return;

        for (String pidStr : expired) {
            long pid = Long.parseLong(pidStr);
            Boolean ok = bidRedisTemplate.opsForValue().setIfAbsent(lockEnd(pid), "1", Duration.ofSeconds(LOCK_SEC));
            if (!Boolean.TRUE.equals(ok)) continue;

            try {
                // DB -> 로드 후 종료
                productRepository.findById(pid).ifPresentOrElse(
                        p -> {
                            try {
                                productService.endAuction(p);
                            } catch (Exception e) {
                                log.warn("[AuctionTick] endAuction 실패 pid={}", pid, e);
                            }
                        },
                        () -> log.debug("[AuctionTick] end 대상 상품 미존재 pid={}", pid)
                );
            } finally {
                // 인덱스 제거 (중복 처리 방지)
                bidRedisTemplate.opsForZSet().remove(Z_ACTIVE, pidStr);
                // baseline 잔존 키 정리(선택) — 서비스 정책에 따라 유지/삭제
                bidRedisTemplate.delete("product_bid_zset_" + pid);
                bidRedisTemplate.delete("product_bid_hash_" + pid);
                bidRedisTemplate.delete(kEndTime(pid));
            }
        }
    }

    /**
     * 4) 알림 Tick: 10분/5분 전 알림을 각 ZSET에서 드레인
     *    - 각 알림마다 락으로 한번만 발송
     */
    @Scheduled(fixedDelay = 5_000L, initialDelay = 40_000L)
    public void notifyTick() {
        long now = nowMs();
        // 10분 전
        drainNotify(Z_NOTIFY_10, now, 10);
        // 5분 전
        drainNotify(Z_NOTIFY_5, now, 5);
    }

    private void drainNotify(String zset, long now, int minutes) {
        Set<String> due = bidRedisTemplate.opsForZSet().rangeByScore(zset, 0, now, 0, CHUNK_SIZE);
        if (due == null || due.isEmpty()) return;

        for (String pidStr : due) {
            long pid = Long.parseLong(pidStr);
            String lockKey = (minutes == 10) ? lockN10(pid) : lockN5(pid);
            Boolean ok = bidRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(LOCK_SEC));
            if (!Boolean.TRUE.equals(ok)) continue;

            try {
                auctionNotificationService.notifyEndingSoon(pid, minutes);
            } catch (Exception e) {
                log.warn("[AuctionTick] notify {}분 전 실패 pid={}", minutes, pid, e);
            } finally {
                bidRedisTemplate.opsForZSet().remove(zset, pidStr);
            }
        }
    }
}