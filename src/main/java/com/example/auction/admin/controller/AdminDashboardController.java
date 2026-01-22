package com.example.auction.admin.controller;

import com.example.auction.admin.dto.AdminDashboardDto;
import com.example.auction.admin.dto.AdminOverviewResponse;
import com.example.auction.admin.service.AdminDashboardService;
import com.example.auction.admin.service.AdminOverviewService;
import com.example.auction.common.dto.CommonResDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;
    private final AdminOverviewService adminOverviewService;

    @PreAuthorize("hasAnyRole('ADMIN','INQUIRY')")
    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard() {
        AdminDashboardDto dto = adminDashboardService.getDashboard();
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "관리자 대시보드", dto));
    }

    @PreAuthorize("hasAnyRole('ADMIN','INQUIRY')")
    @GetMapping("/overview")
    public ResponseEntity<?> overview() {
        AdminOverviewResponse dto = adminOverviewService.getOverview();
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "관리자 개요", dto));
    }

    // 카테고리 분포 관련(overview)
    @GetMapping("/category-distribution")
    public ResponseEntity<?> categoryDistribution() {
        var data = adminOverviewService.getTopLevelCategoryDistribution();
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "카테고리 분포 조회 성공", data));
    }

}
