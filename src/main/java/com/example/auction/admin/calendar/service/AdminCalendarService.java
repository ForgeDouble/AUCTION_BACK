package com.example.auction.admin.calendar.service;

import com.example.auction.admin.calendar.domain.AdminCalendarEvent;
import com.example.auction.admin.calendar.domain.CalendarEventTag;
import com.example.auction.admin.calendar.dto.AdminCalendarEventCreateDto;
import com.example.auction.admin.calendar.dto.AdminCalendarEventResponseDto;
import com.example.auction.admin.calendar.repository.AdminCalendarEventRepository;
import com.example.auction.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminCalendarService {
    private final AdminCalendarEventRepository adminCalendarEventRepository;
    private final UserService userService;

    public AdminCalendarService(AdminCalendarEventRepository adminCalendarEventRepository, UserService userService) {
        this.adminCalendarEventRepository = adminCalendarEventRepository;
        this.userService = userService;
    }

    // 일정 생성
    @Transactional
    public AdminCalendarEventResponseDto create(AdminCalendarEventCreateDto dto) {
        userService.checkAdminAuthority();

        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new IllegalArgumentException("제목은 필수입니다.");
        }
        if (dto.getDate() == null) {
            throw new IllegalArgumentException("날 입력은 필수입니다.");
        }

        CalendarEventTag tag = dto.getTag() == null ? CalendarEventTag.ETC : dto.getTag();

        AdminCalendarEvent saved = adminCalendarEventRepository.save(
                AdminCalendarEvent.builder()
                        .date(dto.getDate())
                        .time(dto.getTime())
                        .title(dto.getTitle().trim())
                        .tag(tag)
                        .build()
        );

        return AdminCalendarEventResponseDto.from(saved);
    }
}
