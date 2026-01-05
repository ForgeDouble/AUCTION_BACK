package com.example.auction.admin.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.auction.admin.dto.ActiveHourBucketDto;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.auction.admin.dto.ActiveHourBucketDto;

@Service
public class AdminMetricsService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate metricsRedis;

    public AdminMetricsService(@Qualifier("metrics") StringRedisTemplate metricsRedis) {
        this.metricsRedis = metricsRedis;
    }

    @Transactional(readOnly = true)
    public List<ActiveHourBucketDto> getTodayActiveUsers3h() {
        String day = LocalDate.now(KST).format(DATE);

        int[] hours = new int[] {0, 3, 6, 9, 12, 15, 18, 21};
        List<ActiveHourBucketDto> out = new ArrayList<>(hours.length);

        for (int h : hours) {
            String hh = String.format("%02d", h);
            String key = "metrics:activeUsers3h:" + day + ":" + hh;

            Long size = null;
            try {
                size = metricsRedis.opsForSet().size(key);
            } catch (Exception ignore) {}

            out.add(ActiveHourBucketDto.builder()
                    .hour(hh)
                    .count(size == null ? 0L : size)
                    .build());
        }

        return out;
    }
}
