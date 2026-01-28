//package com.example.auction.admin.controller;
//
//import com.example.auction.admin.dto.AdminOverviewResponse;
//import com.example.auction.admin.service.AdminOverviewService;
//import com.example.auction.common.dto.CommonResDto;
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.access.prepost.PreAuthorize;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//@RestController
//@RequiredArgsConstructor
//@RequestMapping("/admin")
//public class AdminOverviewController {
//
//    private final AdminOverviewService adminOverviewService;
//
//    @PreAuthorize("hasAnyRole('ADMIN','INQUIRY')")
//    @GetMapping("/overview/broad")
//    public ResponseEntity<?> overview() {
//        AdminOverviewResponse dto = adminOverviewService.getOverview();
//        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "관리자 개요", dto));
//    }
//}
