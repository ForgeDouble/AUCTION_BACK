package com.example.auction.notification.service;

import com.example.auction.bid.service.BidService;
import com.example.auction.bid.dto.BidEvent;
import com.example.auction.common.domain.DelYN;
import com.example.auction.product.domain.Product;
import com.example.auction.product.repository.ProductRepository;
import com.example.auction.push.service.PushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
@Service
@RequiredArgsConstructor
@Slf4j
public class AuctionNotificationService {

    private final PushService pushService;
    private final BidService bidService;
    private final ProductRepository productRepository;
    private final TaskScheduler taskScheduler;
    private final RedisTemplate<String, String> bidStringRedisTemplate;

    private String kStart(Long pid) {
        return "notify:auction:start:" + pid;
    }
    private String kEnd(Long pid)   {
        return "notify:auction:end:" + pid;
    }
    private String kEndSoon(Long pid, int m) {
        return "notify:auction:ending:" + m + ":" + pid;
    }

    private boolean setOnce(String key, long ttlSeconds) {
        Boolean ok = bidStringRedisTemplate.opsForValue().setIfAbsent(key, "1", ttlSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    private long ttlUntil(Instant when, long bufferSec) {
        long secs = Duration.between(Instant.now(), when).getSeconds();
        return Math.max(secs + bufferSec, 60);
    }

    /* 판매자: 경매 시작 알림 */
    public void notifyAuctionStarted(Long productId) {
        Product product = productRepository.findById(productId)
                .filter(x -> x.getDelYn() == DelYN.N && !Boolean.TRUE.equals(x.getBlocked()))
                .orElse(null);
        if (product == null) return;
        var endI = product.getAuctionEndTime().atZone(ZoneId.systemDefault()).toInstant();
        if (!setOnce(kStart(productId), ttlUntil(endI, 3600))) return;

        try {
            pushService.sendToUser(
                    product.getUser().getUserId(),
                    "경매가 시작되었습니다",
                    "등록하신 \"" + product.getProductName() + "\" 경매가 시작됐어요.",
                    Map.of("type","AUCTION_STARTED","productId", String.valueOf(product.getProductId()))
            );
        } catch (Exception e) {
            log.warn("[Notify] 시작 알림 실패 pid={}", productId, e);
        }
    }

    /* 구매자: 종료 임박(10/5분) 알림 */
    public void notifyEndingSoon(Long productId, int minutes) {
        Product product = productRepository.findById(productId)
                .filter(x -> x.getDelYn() == DelYN.N && !Boolean.TRUE.equals(x.getBlocked()))
                .orElse(null);
        if (product == null) return;

        var endI = product.getAuctionEndTime().atZone(ZoneId.systemDefault()).toInstant();
        if (!setOnce(kEndSoon(productId, minutes), ttlUntil(endI, 3600))) return;

        // 입찰자 집합(중복 제거)
        List<BidEvent> history = bidService.getAllBidHistory(productId, true);
        Set<Long> bidderIds = history.stream()
                .map(BidEvent::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (bidderIds.isEmpty()) return;

        String title = (minutes == 10) ? "종료 10분 전" : "종료 5분 전";
        String body  = "\"" + product.getProductName() + "\" 경매가 곧 종료됩니다.";

        for (Long uid : bidderIds) {
            try {
                pushService.sendToUser(uid, title, body,
                        Map.of("type","AUCTION_ENDING_SOON",
                                "productId", String.valueOf(product.getProductId()),
                                "minutes", String.valueOf(minutes)));
            } catch (Exception e) {
                log.warn("[Notify] 임박({}분) 알림 실패 pid={}, uid={}", minutes, productId, uid, e);
            }
        }
    }

    /* 상품 판매자&낙찰자 : 경매 종료 알림 */
    public void notifyAuctionEnded(Long productId, Long winnerUserId, String productName) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) return;

        var endI = product.getAuctionEndTime().atZone(ZoneId.systemDefault()).toInstant();
        if (!setOnce(kEnd(productId), ttlUntil(endI, 3600))) {
            return;
        }

        // 판매자
        try {
            pushService.sendToUser(
                    product.getUser().getUserId(),
                    "경매가 종료되었습니다",
                    "등록하신 \"" + productName + "\" 경매가 종료됐어요.",
                    Map.of("type","AUCTION_ENDED","productId", String.valueOf(productId))
            );
        } catch (Exception e) {
            log.warn("[Notify] 종료 알림(판매자) 실패 pid={}", productId, e);
        }

        // 낙찰자
        if (winnerUserId != null) {
            try {
                pushService.sendToUser(
                        winnerUserId,
                        "축하합니다! 낙찰되셨습니다",
                        "\"" + productName + "\" 경매를 낙찰 받으셨어요.",
                        Map.of("type","AUCTION_WON","productId", String.valueOf(productId))
                );
            } catch (Exception e) {
                log.warn("[Notify] 종료 알림(낙찰자) 실패 pid={}, uid={}", productId, winnerUserId, e);
            }
        }
    }

    /* 종료 10분전 + 5분 전 알림 */
    public void scheduleEndingSoonJobs(Long productId, Instant endInstant) {
        Instant ten = endInstant.minus(Duration.ofMinutes(10));
        Instant five = endInstant.minus(Duration.ofMinutes(5));

        if (ten.isAfter(Instant.now())) {
            taskScheduler.schedule(() -> {
                try { notifyEndingSoon(productId, 10); } catch (Exception ignore) {}
            }, ten);
        }
        if (five.isAfter(Instant.now())) {
            taskScheduler.schedule(() -> {
                try { notifyEndingSoon(productId, 5); } catch (Exception ignore) {}
            }, five);
        }
    }
}