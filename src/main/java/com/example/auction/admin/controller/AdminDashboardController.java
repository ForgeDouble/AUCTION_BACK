package com.example.auction.admin.controller;

import com.example.auction.admin.dto.AdminDashboardDto;
import com.example.auction.admin.service.AdminDashboardService;
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

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard() {
        AdminDashboardDto dto = adminDashboardService.getDashboard();
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "관리자 대시보드", dto));
    }


}
