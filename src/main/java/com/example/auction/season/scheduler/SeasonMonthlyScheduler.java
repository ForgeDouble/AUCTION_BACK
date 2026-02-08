package com.example.auction.season.scheduler;

import com.example.auction.season.service.SeasonMonthlyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.ZoneId;

@Component
@Slf4j
@RequiredArgsConstructor
public class SeasonMonthlyScheduler {

    private final SeasonMonthlyService seasonMonthlyService;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // 매월 1일 00:10
    @Scheduled(cron = "0 10 0 1 * *", zone = "Asia/Seoul")
    public void run() {
        YearMonth target = YearMonth.now(KST).minusMonths(1);
        log.info("[Season] 달 스케줄러 시작 target={}", target);
        seasonMonthlyService.runForMonth(target, true);
    }
}
