package com.example.auction.admin.service;

import com.example.auction.product.domain.Status;
import com.example.auction.product.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class OngoingAuctionMetricsService {

    private static final String KEY_ONGOING = "metrics:ongoingAuctions";
    private static final String LOCK_KEY = "lock:metrics:ongoingAuctions";

    @Qualifier("presence")
    private final StringRedisTemplate presenceRedis;
    private final ProductRepository productRepository;

    public OngoingAuctionMetricsService(@Qualifier("presence")StringRedisTemplate presenceRedis, ProductRepository productRepository) {
        this.presenceRedis = presenceRedis;
        this.productRepository = productRepository;
    }

    public long getOngoingAuctionsCached() {
        String v = presenceRedis.opsForValue().get(KEY_ONGOING);
        if (v != null) {
            try { return Long.parseLong(v); } catch (Exception ignored) {}
        }
        long fresh = queryDbOngoing();
        presenceRedis.opsForValue().set(KEY_ONGOING, String.valueOf(fresh), Duration.ofSeconds(10));
        return fresh;
    }

    // 5초마다 1번만 갱신
    @Scheduled(fixedDelay = 5000)
    public void refreshOngoingAuctionsCache() {

        Boolean locked = presenceRedis.opsForValue().setIfAbsent(LOCK_KEY, "1", Duration.ofSeconds(8));
        if (!Boolean.TRUE.equals(locked)) return;

        long fresh = queryDbOngoing();
        presenceRedis.opsForValue().set(KEY_ONGOING, String.valueOf(fresh), Duration.ofSeconds(10));
    }

    private long queryDbOngoing() {
        List<Status> statuses = List.of(Status.READY, Status.PROCESSING);
        return productRepository.countByStatusInAndBlockedFalse(statuses);
    }
}
