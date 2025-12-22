package com.example.auction.admin.dto;

import lombok.*;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminOverviewResponse {

    private long todayNewUsers; // 오늘 생성 유저
    private long todayCreatedAuctions; // 오늘 생성 관련 (경매수 / 종료 경매수 / 판매수)
    private long todayEndedAuctions; // 오늘 만료 경매
    private long todaySoldAuctions; // 오늘 판매 경매

    private long totalBids;  // 오늘 총 입찰액
    private long ongoingAuctions;  // 진행 중인 경매
    private long reportsOpen; // 미처리 신고 개수

    private long realtimeUsers; // 실시간 접속 유저
    private long todayActiveUsers; // 오늘 접속 유저

    private long todayTradeAmount; // 금일 총 거래금액
    private long monthlyAvgTradeAmount; // 최근 N개월 평균
    private List<HourlyPoint> todayActivityHourly; // 오늘 평균 사용 시간대

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class HourlyPoint {
        private int hour;
        private long users;
    }
}
