package com.example.auction.admin.service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

import com.example.auction.admin.dto.AdminAuctionTrendRowDto;
import com.example.auction.admin.dto.AdminMonthlyTradeRowDto;
import com.example.auction.product.domain.Status;
import com.example.auction.product.repository.ProductRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.auction.admin.dto.ActiveHourBucketDto;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;



import org.springframework.beans.factory.annotation.Qualifier;

@Service
public class AdminMetricsService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate metricsRedis;
    private final ProductRepository productRepository;

    public AdminMetricsService(@Qualifier("metrics") StringRedisTemplate metricsRedis, ProductRepository productRepository) {
        this.metricsRedis = metricsRedis;
        this.productRepository = productRepository;
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

    // 최근 7일 내 생성/종료 확인용 (그래프 지표)
    @Transactional(readOnly = true)
    public List<AdminAuctionTrendRowDto> auctionTrend(int days) {
        int day = Math.max(1, Math.min(days, 30));

        LocalDate today = LocalDate.now(KST);
        LocalDate startDate = today.minusDays(day - 1);
        LocalDate endExclusiveDate = today.plusDays(1);

        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to = endExclusiveDate.atStartOfDay();

        Map<LocalDate, Long> createdMap = toMap(
                productRepository.countCreatedDaily(from, to));
        Map<LocalDate, Long> endedMap = toMap(
                productRepository.countEndedDaily(from, to, List.of(Status.SELLED, Status.NOTSELLED)));

        List<AdminAuctionTrendRowDto> out = new ArrayList<>(day);
        for (int i = 0; i < day; i++) {
            LocalDate date = startDate.plusDays(i);
            long created = createdMap.getOrDefault(date, 0L);
            long ended = endedMap.getOrDefault(date, 0L);
            out.add(new AdminAuctionTrendRowDto(date.toString(), created, ended));
        }
        return out;
    }

    private Map<LocalDate, Long> toMap(List<ProductRepository.DayCountRow> rows) {
        Map<LocalDate, Long> map = new HashMap<>();
        if (rows == null) return map;

        for (var r : rows) {
            Date date = (Date) r.getD();
            if (date == null) continue;
            LocalDate ld = date.toLocalDate();
            long cnt = (r.getCnt() == null ? 0L : r.getCnt());
            map.put(ld, cnt);
        }
        return map;
    }

    // 월별 거래 금액 추이 확인 지표
    @Transactional(readOnly = true)
    public List<AdminMonthlyTradeRowDto> monthlyTrade(int months) {
        int m = Math.max(1, Math.min(months, 24));

        LocalDate today = LocalDate.now(KST);

        // 조회 시작 끝 달 확인용
        LocalDate startMonth = today.withDayOfMonth(1).minusMonths(m - 1);
        LocalDate endExclusiveMonth = today.withDayOfMonth(1).plusMonths(1);

        LocalDateTime from = startMonth.atStartOfDay();
        LocalDateTime to = endExclusiveMonth.atStartOfDay();

        var rows = productRepository.sumMonthlyTradeAmountSold(from, to);

        Map<String, Long> map = new HashMap<>();
        if (rows != null) {
            for (var r : rows) {
                String ym = r.getYm();
                long amount = (r.getAmount() == null ? 0L : r.getAmount());
                if (ym != null && !ym.isBlank()) map.put(ym, amount);
            }
        }

        List<AdminMonthlyTradeRowDto> out = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            LocalDate month = startMonth.plusMonths(i);
            String ym = month.getYear() + "-" + String.format("%02d", month.getMonthValue());
            out.add(new AdminMonthlyTradeRowDto(ym, map.getOrDefault(ym, 0L)));
        }
        return out;
    }


}
