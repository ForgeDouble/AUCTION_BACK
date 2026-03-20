package com.example.auction.product.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

@Service
public class ProductCountCacheService {

    private final StringRedisTemplate redisTemplate;

    @Value("${cache.product-count-ttl-seconds:10}")
    private long ttlSeconds;

    public ProductCountCacheService(
            @Qualifier("productCache") StringRedisTemplate stringRedisTemplate
    ) {
        this.redisTemplate = stringRedisTemplate;
    }

    public long getOrLoad(String key, Supplier<Long> loader) {
        String cached = redisTemplate.opsForValue().get(key);

        if (cached != null && !cached.isBlank()) {
            try {
                return Long.parseLong(cached);
            } catch (NumberFormatException ignore) {
            }
        }

        long value = loader.get();
        redisTemplate.opsForValue().set(
                key,
                String.valueOf(value),
                Duration.ofSeconds(ttlSeconds)
        );
        return value;
    }
}