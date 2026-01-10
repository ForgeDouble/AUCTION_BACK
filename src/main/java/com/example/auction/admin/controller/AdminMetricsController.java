package com.example.auction.admin.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.auction.admin.dto.ActiveHourBucketDto;
import com.example.auction.admin.service.AdminMetricsService;
import com.example.auction.common.dto.CommonResDto;

import lombok.RequiredArgsConstructor;

import java.util.List;

@RestController
@RequestMapping("/admin/metrics")
@RequiredArgsConstructor
public class AdminMetricsController {

    private final AdminMetricsService adminMetricsService;

    // 금일 사용자 주 사용 시간대(3시간 버킷)
    @PreAuthorize("hasAuthority('ADMIN') or hasRole('ADMIN')")
    @GetMapping("/active-hours")
    public ResponseEntity<?> getActiveHours3h() {
        List<ActiveHourBucketDto> list = adminMetricsService.getTodayActiveUsers3h();
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "금일 사용자 활동 시간대 조회 성공", list));
    }

    // 최근 N일 경매 생성/종료 추이
    @PreAuthorize("hasAuthority('ADMIN') or hasRole('ADMIN')")
    @GetMapping("/auction-trend")
    public ResponseEntity<?> auctionTrend(@RequestParam(defaultValue = "7") int days) {
        var list = adminMetricsService.auctionTrend(days);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "최근 경매 생성/종료 추이 조회 성공", list));
    }
}
