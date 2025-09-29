package com.example.auction.report.controller;

import com.example.auction.common.dto.CommonResDto;
import com.example.auction.report.domain.ReportCategory;
import com.example.auction.report.domain.ReportStatus;
import com.example.auction.report.dto.AdminReportGroupDto;
import com.example.auction.report.dto.AdminResolveDto;
import com.example.auction.report.dto.ReportCreateDto;
import com.example.auction.report.dto.ReportResponseDto;
import com.example.auction.report.service.ReportService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/report")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }
    // 유저 → 유저 신고
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/create")
    public ResponseEntity<CommonResDto> create(@RequestBody ReportCreateDto dto) {
        ReportResponseDto reportResponseDto = reportService.create(dto);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "신고 접수 완료", reportResponseDto));
    }

    // [관리자] 특정 유저의 특정 카테고리 신고 묶음 처리 (수락/반려)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/resolve/{targetUserId}/{category}")
    public ResponseEntity<CommonResDto> adminResolve(@PathVariable Long targetUserId,
                                                     @PathVariable ReportCategory category,
                                                     @RequestBody AdminResolveDto adminResolveDto) {
        reportService.adminResolveCategoryForUser(targetUserId, category, adminResolveDto);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "관리자 처리 완료", null));
    }

    // [관리자] 묶음(그룹) 요약 리스트
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/groups")
    public ResponseEntity<CommonResDto> getAdminReportGroups(
            @RequestParam(required = false) ReportCategory category,
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) Integer minPending,
            @RequestParam(required = false) Long targetUserId
    ) {
        List<AdminReportGroupDto> list = reportService.getAdminReportGroups(category, status, minPending, targetUserId);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "그룹 신고 목록 조회 성공", list));
    }

    // [관리자] 특정 그룹(타겟×카테고리) 상세 신고들 페이징
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/groups/{targetUserId}/{category}")
    public ResponseEntity<CommonResDto> getGroupReports(
            @PathVariable Long targetUserId,
            @PathVariable ReportCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        var pageRes = reportService.getGroupReports(targetUserId, category, pageable);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "그룹 신고 상세 조회 성공", pageRes));
    }


    // [관리자] 즉시 정지
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/suspend")
    public ResponseEntity<CommonResDto> adminSuspend(@RequestParam Long targetUserId,
                                                     @RequestParam long days,
                                                     @RequestParam(required = false) String reason) {
        reportService.adminSuspendUser(targetUserId, days, reason);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "정지 처리 완료", null));
    }

    // [관리자] 모든 제재 해제 (정지/임시정지 해제)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/lift")
    public ResponseEntity<CommonResDto> adminLift(@RequestParam Long targetUserId,
                                                  @RequestParam(required = false) String reason) {
        reportService.adminLiftAll(targetUserId, reason);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "제재 해제 완료", null));
    }
}
