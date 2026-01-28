package com.example.auction.admin.calendar.controller;

import com.example.auction.admin.calendar.dto.AdminCalendarEventCreateDto;
import com.example.auction.admin.calendar.dto.AdminCalendarEventDateUpdateDto;
import com.example.auction.admin.calendar.dto.AdminCalendarEventResponseDto;
import com.example.auction.admin.calendar.dto.AdminCalendarEventUpdateDto;
import com.example.auction.admin.calendar.service.AdminCalendarService;
import com.example.auction.common.dto.CommonResDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/calendar")
public class AdminCalendarController {
    private final AdminCalendarService adminCalendarService;

    public AdminCalendarController(AdminCalendarService adminCalendarService) {
        this.adminCalendarService = adminCalendarService;
    }

    private Long parseEventId(String eventId) {
        if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId가 비었습니다.");
        String raw = eventId.trim();
        if (raw.startsWith("E-")) raw = raw.substring(2);
        return Long.parseLong(raw);
    }

    @PreAuthorize("hasAnyRole('ADMIN','INQUIRY')")
    @PostMapping("/events")
    public ResponseEntity<CommonResDto> create(@RequestBody AdminCalendarEventCreateDto adminCalendarEventCreateDto) {
        AdminCalendarEventResponseDto created = adminCalendarService.create(adminCalendarEventCreateDto);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "운영 캘린더 일정 등록 성공", created));
    }

    @PreAuthorize("hasAnyRole('ADMIN','INQUIRY')")
    @GetMapping("/events")
    public ResponseEntity<CommonResDto> list() {
        List<AdminCalendarEventResponseDto> list = adminCalendarService.listEvents();
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "운영 캘린더 조회 성공", list));
    }

    @PreAuthorize("hasAnyRole('ADMIN','INQUIRY')")
    @PutMapping("/events/{eventId}")
    public ResponseEntity<CommonResDto> update(@PathVariable String eventId, @RequestBody AdminCalendarEventUpdateDto dto) {
        AdminCalendarEventResponseDto updated = adminCalendarService.update(parseEventId(eventId), dto);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "일정 수정 성공", updated));
    }

    @PreAuthorize("hasAnyRole('ADMIN','INQUIRY')")
    @PatchMapping("/events/{eventId}/date")
    public ResponseEntity<CommonResDto> moveDate(@PathVariable String eventId, @RequestBody AdminCalendarEventDateUpdateDto dto) {
        AdminCalendarEventResponseDto updated = adminCalendarService.moveDate(parseEventId(eventId), dto.getDate());
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "날짜 변경 성공", updated));
    }

    @PreAuthorize("hasAnyRole('ADMIN','INQUIRY')")
    @DeleteMapping("/events/{eventId}")
    public ResponseEntity<CommonResDto> delete(@PathVariable String eventId) {
        adminCalendarService.delete(parseEventId(eventId));
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "일정 삭제 성공", null));
    }
}
