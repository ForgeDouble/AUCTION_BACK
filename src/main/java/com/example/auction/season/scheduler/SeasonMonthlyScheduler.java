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
    //테스트 환경 조성을 위한 15분 설정
//    @Scheduled(cron = "0 */15 * * * *", zone = "Asia/Seoul")
    public void run() {
        // 데이터가 이번달 밖에 없기 때문에 이번달 셋팅
//        YearMonth target = YearMonth.now(KST).minusMonths(1);
        YearMonth target = YearMonth.now(KST);
        log.info("[Season] 달 스케줄러 시작 target={}", target);
        seasonMonthlyService.runForMonth(target, true);
    }
}
