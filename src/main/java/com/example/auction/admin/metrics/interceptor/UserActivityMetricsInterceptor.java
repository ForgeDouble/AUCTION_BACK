package com.example.auction.admin.metrics.interceptor;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class UserActivityMetricsInterceptor implements HandlerInterceptor {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Duration TTL = Duration.ofDays(3);

    private final StringRedisTemplate metricsRedis;

    public UserActivityMetricsInterceptor(@Qualifier("metrics") StringRedisTemplate metricsRedis) {
        this.metricsRedis = metricsRedis;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return true;

        if (!authentication.isAuthenticated()) return true;
        String email = authentication.getName();
        if (email == null || email.isBlank() || "anonymousUser".equalsIgnoreCase(email)) return true;

        // 관리자 제외
        if (hasAdminAuthority(authentication)) return true;

        ZonedDateTime now = ZonedDateTime.now(KST);
        int hour = now.getHour();
        int bucketStart = (hour / 3) * 3;

        String day = LocalDate.now(KST).format(DATE);
        String hh = String.format("%02d", bucketStart);

        String key = "metrics:activeUsers3h:" + day + ":" + hh;

        try {
            metricsRedis.opsForSet().add(key, email);
            metricsRedis.expire(key, TTL);
        } catch (Exception ignore) {}

        return true;
    }

    private boolean hasAdminAuthority(Authentication auth) {
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if (ga == null) continue;
            String a = ga.getAuthority();
            if (a == null) continue;
            if ("ADMIN".equalsIgnoreCase(a) || "ROLE_ADMIN".equalsIgnoreCase(a)) return true;
        }
        return false;
    }
}