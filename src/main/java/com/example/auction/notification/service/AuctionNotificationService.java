package com.example.auction.notification.service;

import com.example.auction.bid.repository.WishlistQueryPort;
import com.example.auction.bid.service.BidService;
import com.example.auction.bid.dto.BidEvent;
import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.product.domain.Product;
import com.example.auction.product.repository.ProductRepository;
import com.example.auction.push.service.PushService;
import com.example.auction.notification.domain.NotificationCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

// 경매 관련 알림 서비스
@Service
@Slf4j
public class AuctionNotificationService {

    private final PushService pushService;
    private final BidService bidService;
    private final ProductRepository productRepository;
    private final TaskScheduler taskScheduler;
    private final RedisTemplate<String, String> bidStringRedisTemplate;
    private final WishlistQueryPort wishlistQueryPort;

    public AuctionNotificationService(PushService pushService, BidService bidService, ProductRepository productRepository, TaskScheduler taskScheduler, @Qualifier("bidPrice") RedisTemplate<String, String> bidStringRedisTemplate,
                                      WishlistQueryPort wishlistQueryPort) {
        this.pushService = pushService;
        this.bidService = bidService;
        this.productRepository = productRepository;
        this.taskScheduler = taskScheduler;
        this.bidStringRedisTemplate = bidStringRedisTemplate;
        this.wishlistQueryPort = wishlistQueryPort;
    }


    private String kStart(Long pid) {
        return "notify:auction:start:" + pid;
    }
    private String kEnd(Long pid)   {
        return "notify:auction:end:" + pid;
    }
    private String kStartSoon(Long pid, int m) {
        return "notify:auction:startSoon:" + m + ":" + pid;
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
        return Math.max(60, secs + bufferSec);
    }
    private long ttlFixed(long seconds) {
        return Math.max(60, seconds);
    }

    //  경매 시작 10/5분 전 알림 (판매자만)
    public void notifyStartingSoon(Long productId, int minutes) {
        if (minutes != 10 && minutes != 5) return;

        Product product = productRepository.findById(productId)
                .filter(x -> x.getDelYn() == DelYN.N && !Boolean.TRUE.equals(x.getBlocked()))
                .orElse(null);
        if (product == null) return;

        Long sellerId = product.getUser() != null ? product.getUser().getUserId() : null;
        if (sellerId == null) return;

        Instant startInstant = product.getAuctionStartTime()
                .atZone(ZoneId.systemDefault())
                .toInstant();

        // 시작 직전 알림은 중복 방지 (시작 이후에도 재처리될 수 있으니 TTL은 시작+1시간 정도로)
        if (!setOnce(kStartSoon(productId, minutes), ttlUntil(startInstant, 3600))) return;

        String title = "경매 시작 " + minutes + "분 전";
        String body = "등록하신 [" + product.getProductName() + "] 경매가 곧 시작됩니다.";

        Map<String, String> data = Map.of(
                "type", "AUCTION_STARTING_SOON",
                "productId", String.valueOf(productId),
                "minutes", String.valueOf(minutes)
        );

        try {
            pushService.sendToUser(
                    sellerId,
                    title,
                    body,
                    data,
                    NotificationCategory.AUCTION,
                    true
            );
        } catch (Exception e) {
            log.warn("[Notify] 시작 {}분 전 알림 실패 pid={}, sellerId={}", minutes, productId, sellerId, e);
        }
    }

    /* 판매자: 경매 시작 알림 */
    public void notifyAuctionStarted(Long productId) {
        Product product = productRepository.findById(productId)
                .filter(x -> x.getDelYn() == DelYN.N && !Boolean.TRUE.equals(x.getBlocked()))
                .orElse(null);
        if (product == null) return;

        Instant endInstant = product.getAuctionEndTime()
                .atZone(ZoneId.systemDefault())
                .toInstant();

        Long sellerId = product.getUser() != null ? product.getUser().getUserId() : null;

        Set<Long> recipients = new HashSet<>();
        if (sellerId != null) recipients.add(sellerId);

        try {
            Set<Long> wishlisters = wishlistQueryPort.findWishlisterUserIdsByProductId(productId);
            if (wishlisters != null) recipients.addAll(wishlisters);
        } catch (Exception ignore) { }

        if (recipients.isEmpty()) return;

        for (Long uid : recipients) {
            try {
                boolean isSeller = (sellerId != null && sellerId.equals(uid));
                String title = "경매가 시작되었습니다";
                String body = isSeller
                        ? "등록하신 [" + product.getProductName() + "] 상품의 경매가 시작했습니다."
                        : "관심 상품 [" + product.getProductName() + "] 경매가 시작했습니다.";

                Map<String, String> data = Map.of(
                        "type", "AUCTION_STARTED",
                        "productId", String.valueOf(productId)
                );

                pushService.sendToUser(uid, title, body, data, NotificationCategory.AUCTION, true);
            } catch (Exception e) {
                log.warn("[Notify] 시작 알림 실패 uid={}, pid={}", uid, productId, e);
            }
        }
    }

    // 경매 종료 10분 전 -> 판매자 + 전체 입찰자 + 찜 유저
    // 경매 종료 5분 전 -> 판매자
    public void notifyEndingSoon(Long productId, int minutes) {
        if (minutes != 10 && minutes != 5) return;

        Product product = productRepository.findById(productId)
                .filter(x -> x.getDelYn() == DelYN.N && !Boolean.TRUE.equals(x.getBlocked()))
                .orElse(null);
        if (product == null) return;

        Instant endInstant = product.getAuctionEndTime()
                .atZone(ZoneId.systemDefault())
                .toInstant();

        if (!setOnce(kEndSoon(productId, minutes), ttlUntil(endInstant, 3600))) return;

        Long sellerId = product.getUser() != null ? product.getUser().getUserId() : null;

        Set<Long> recipients = new HashSet<>();

        if (minutes == 10) {
            if (sellerId != null) recipients.add(sellerId);

            // 전체 입찰자
            try {
                List<BidEvent> history = bidService.getAllBidHistory(productId, true);
                if (history != null) {
                    for (BidEvent ev : history) {
                        if (ev != null && ev.getUserId() != null) recipients.add(ev.getUserId());
                    }
                }
            } catch (Exception e) {
                log.warn("[Notify] 입찰자 조회 실패 pid={}", productId, e);
            }

            // 찜 유저
            try {
                Set<Long> wishlisters = wishlistQueryPort.findWishlisterUserIdsByProductId(productId);
                if (wishlisters != null) recipients.addAll(wishlisters);
            } catch (Exception ignore) { }

        } else {
            // 5분 전은 판매자만
            if (sellerId != null) recipients.add(sellerId);
        }

        if (recipients.isEmpty()) return;

        String title = "경매 종료 " + minutes + "분 전";
        String body = "[" + product.getProductName() + "] 경매가 곧 종료됩니다.";

        Map<String, String> data = Map.of(
                "type", "AUCTION_ENDING_SOON",
                "productId", String.valueOf(productId),
                "minutes", String.valueOf(minutes)
        );

        for (Long uid : recipients) {
            try {
                pushService.sendToUser(uid, title, body, data, NotificationCategory.AUCTION, true);
            } catch (Exception e) {
                log.warn("[Notify] 종료임박 알림 실패 uid={}, pid={}", uid, productId, e);
            }
        }
    }

    /* 상품 판매자&낙찰자 : 경매 종료 알림 */
    public void notifyAuctionEndedWithWinner(Long productId, BidEvent winner, String productName) {
        if (winner == null || winner.getUserId() == null) {
            log.warn("[AuctionNotify] winner null 또는 userId null productId={}", productId);
            return;
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("상품이 존재하지 않습니다."));

        if (!setOnce(kEnd(productId), ttlFixed(3 * 24 * 60 * 60))) return;

        Long sellerId = product.getUser() != null ? product.getUser().getUserId() : null;
        String safeName = (productName != null && !productName.isBlank())
                ? productName
                : product.getProductName();

        String amountStr = NumberFormat.getInstance(Locale.KOREA)
                .format(winner.getBidAmount());

        // 판매자 알림
        if (sellerId != null) {
            try {
                pushService.sendToUser(
                        sellerId,
                        "경매 종료",
                        "등록하신 [" + safeName + "] 상품의 경매가 " + amountStr + "원에 낙찰되었습니다.",
                        Map.of(
                                "type", "AUCTION_ENDED",
                                "productId", String.valueOf(productId),
                                "winnerUserId", String.valueOf(winner.getUserId()),
                                "amount", String.valueOf(winner.getBidAmount())
                        ),
                        NotificationCategory.AUCTION,
                        true
                );
            } catch (Exception e) {
                log.warn("[AuctionNotify] 판매자 알림 실패 productId={}, sellerId={}", productId, sellerId, e);
            }
        }

        // 낙찰자 알림
        try {
            pushService.sendToUser(
                    winner.getUserId(),
                    "입찰 상품 낙찰",
                    "[" + safeName + "]을(를) " + amountStr + "원에 낙찰 받으셨습니다.",
                    Map.of(
                            "type", "AUCTION_WINNER",
                            "productId", String.valueOf(productId),
                            "amount", String.valueOf(winner.getBidAmount())
                    ),
                    NotificationCategory.AUCTION,
                    true
            );
        } catch (Exception e) {
            log.warn("[AuctionNotify] 낙찰자 알림 실패 productId={}, winnerUserId={}", productId, winner.getUserId(), e);
        }
    }

    // 낙찰자가 없는 경우의 경매 종료 알림
    public void notifyAuctionEndedNoWinner(Long productId, String productName) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("상품이 존재하지 않습니다."));

        if (!setOnce(kEnd(productId), ttlFixed(3 * 24 * 60 * 60))) return;

        Long sellerId = product.getUser() != null ? product.getUser().getUserId() : null;
        String safeName = (productName != null && !productName.isBlank())
                ? productName
                : product.getProductName();

        if (sellerId == null) return;

        try {
            pushService.sendToUser(
                    sellerId,
                    "경매 종료",
                    "등록하신 [" + safeName + "] 경매가 입찰자 없이 종료되었습니다.",
                    Map.of(
                            "type", "AUCTION_ENDED_NO_WINNER",
                            "productId", String.valueOf(productId)
                    ),
                    NotificationCategory.AUCTION,
                    true
            );
        } catch (Exception e) {
            log.warn("[AuctionNotify] 판매자(무낙찰) 알림 실패 productId={}, sellerId={}", productId, sellerId, e);
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

    /* 직전 최고 입찰자가 다른 유저에게 밀렸을 때 알림 */
    public void notifyOutbid(Long productId, Long previousUserId, Long lastAmount, Long newAmount, String productName) {
        if (previousUserId == null) {
            log.warn("[AuctionNotify] previousUserId null Outbid 스킵 productId={}", productId);
            return;
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("상품이 존재하지 않습니다."));

        String safeName = (productName != null && !productName.isBlank())
                ? productName
                : product.getProductName();

        String lastStr = NumberFormat.getInstance(Locale.KOREA).format(lastAmount);
        String newStr  = NumberFormat.getInstance(Locale.KOREA).format(newAmount);

        String title = "입찰가가 추월되었습니다";
        String body  = "[" + safeName + "] 경매에서 "
                + newStr + "원으로 새로운 최고 입찰가가 등록되어 "
                + lastStr + "원 입찰이 밀렸습니다.";

        Map<String,String> data = Map.of(
                "type", "BID_OUTBID",
                "productId", String.valueOf(productId),
                "lastAmount", String.valueOf(lastAmount),
                "newAmount", String.valueOf(newAmount)
        );

        try {
            pushService.sendToUser(previousUserId, title, body, data, NotificationCategory.AUCTION, true);
        } catch (Exception e) {
            log.warn("[AuctionNotify] Outbid 알림 실패 productId={}, previousUserId={}", productId, previousUserId, e);
        }
    }
}