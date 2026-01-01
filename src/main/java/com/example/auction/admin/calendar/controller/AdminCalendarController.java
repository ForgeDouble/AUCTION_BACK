package com.example.auction.admin.calendar.controller;

import com.example.auction.admin.calendar.dto.AdminCalendarEventCreateDto;
import com.example.auction.admin.calendar.dto.AdminCalendarEventResponseDto;
import com.example.auction.admin.calendar.service.AdminCalendarService;
import com.example.auction.common.dto.CommonResDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/calendar")
public class AdminCalendarController {
    private final AdminCalendarService adminCalendarService;

    public AdminCalendarController(AdminCalendarService adminCalendarService) {
        this.adminCalendarService = adminCalendarService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/events")
    public ResponseEntity<CommonResDto> create(@Validated @RequestBody AdminCalendarEventCreateDto adminCalendarEventCreateDto) {
        AdminCalendarEventResponseDto created = adminCalendarService.create(adminCalendarEventCreateDto);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "운영 캘린더 일정 등록 성공", created));
    }
}
