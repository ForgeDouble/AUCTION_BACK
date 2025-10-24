package com.example.auction.notification.scheduler;

import com.example.auction.common.domain.DelYN;
import com.example.auction.notification.service.AuctionNotificationService;
import com.example.auction.product.domain.Product;
import com.example.auction.product.repository.ProductRepository;
import com.example.auction.product.domain.SellYN;
import com.example.auction.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuctionTickScheduler {

    private final ProductRepository productRepository;
    private final ProductService productService;
    private final AuctionNotificationService auctionNotificationService;

    @Scheduled(fixedDelay = 60_000L, initialDelay = 15_000L)
    public void tick() {
        LocalDateTime now = LocalDateTime.now();

        List<Product> candidates = productRepository.findBySellYNAndDelYnAndBlocked(SellYN.N, DelYN.N, false);
        if (candidates.isEmpty()) return;

        for (Product product : candidates) {
            LocalDateTime start = product.getAuctionStartTime();
            LocalDateTime end   = product.getAuctionEndTime();

            if (now.isBefore(start)) continue;

            // 시작: baseline 및 시작 알림
            try { productService.startAuction(product.getProductId()); } catch (Exception ignore) {}

            if (now.isBefore(end)) {
                long m = java.time.temporal.ChronoUnit.MINUTES.between(now, end);
                if (m <= 10 && m > 9) auctionNotificationService.notifyEndingSoon(product.getProductId(), 10);
                if (m <= 5  && m > 4) auctionNotificationService.notifyEndingSoon(product.getProductId(), 5);
                continue;
            }

            // 종료
            try { productService.endAuction(product); } catch (Exception e) {
                log.warn("[Tick] endAuction 실패 pid={}", product.getProductId(), e);
            }
        }
    }
}
