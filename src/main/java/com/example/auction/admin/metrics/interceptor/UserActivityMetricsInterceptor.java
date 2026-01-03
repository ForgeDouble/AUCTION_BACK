package com.example.auction.admin.metrics.interceptor;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

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

    private final StringRedisTemplate stringRedisTemplate;

    public UserActivityMetricsInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return true;

        if (!auth.isAuthenticated()) return true;
        String email = auth.getName();
        if (email == null || email.isBlank() || "anonymousUser".equalsIgnoreCase(email)) return true;

        if (hasAdminAuthority(auth)) return true;

        // 오늘 + 3시간 버킷(00/03/06/09/12/15/18/21)
        ZonedDateTime now = ZonedDateTime.now(KST);
        int hour = now.getHour();
        int bucketStart = (hour / 3) * 3;

        String day = LocalDate.now(KST).format(DATE);
        String hh = String.format("%02d", bucketStart);

        String key = "metrics:activeUsers3h:" + day + ":" + hh;

        try {
            stringRedisTemplate.opsForSet().add(key, email);
            stringRedisTemplate.expire(key, TTL);
        } catch (Exception ignore) {
        }

        return true;
    }

    private boolean hasAdminAuthority(Authentication auth) {
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if (ga == null) continue;
            String a = ga.getAuthority();
            if (a == null) continue;
            // 프로젝트에 따라 "ADMIN" 또는 "ROLE_ADMIN" 형태일 수 있어서 둘 다 허용
            if ("ADMIN".equalsIgnoreCase(a) || "ROLE_ADMIN".equalsIgnoreCase(a)) return true;
        }
        return false;
    }
}