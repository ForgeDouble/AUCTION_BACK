package com.example.auction.admin.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class AdminReportCounter {

    @Qualifier("reportCounter")
    private final StringRedisTemplate reportCounter;

    private static final String OPEN_REPORTS_KEY = "report:open";

    public AdminReportCounter(@Qualifier("reportCounter")StringRedisTemplate reportCounter) {
        this.reportCounter = reportCounter;
    }

    public long getOpenReportsCount() {
        String v = reportCounter.opsForValue().get(OPEN_REPORTS_KEY);
        if (v == null || v.isBlank()) return 0;
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
