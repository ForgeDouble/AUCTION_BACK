package com.example.auction.common.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class CustomTokenExpiredStrategy {

    @Qualifier("login")
    private final RedisTemplate<String, Object> loginRedis;

    private static final String KEY_PREFIX = "login:token:";

    public void save(String email, String token, long ttlSeconds) {
        loginRedis.opsForValue().set(KEY_PREFIX + email, token, Duration.ofSeconds(ttlSeconds));
    }

    public String get(String email) {
        Object val = loginRedis.opsForValue().get(KEY_PREFIX + email);
        return val == null ? null : val.toString();
    }

    public void delete(String email) {
        loginRedis.delete(KEY_PREFIX + email);
    }
}
