package com.example.auction.admin.calendar.service;

import com.example.auction.admin.calendar.domain.AdminCalendarEvent;
import com.example.auction.admin.calendar.domain.CalendarEventTag;
import com.example.auction.admin.calendar.dto.AdminCalendarEventCreateDto;
import com.example.auction.admin.calendar.dto.AdminCalendarEventResponseDto;
import com.example.auction.admin.calendar.dto.AdminCalendarEventUpdateDto;
import com.example.auction.admin.calendar.repository.AdminCalendarEventRepository;
import com.example.auction.common.domain.DelYN;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import com.example.auction.user.service.UserService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

@Service
public class AdminCalendarService {
    private final AdminCalendarEventRepository adminCalendarEventRepository;
    private final UserService userService;
    private final UserRepository userRepository;

    public AdminCalendarService(AdminCalendarEventRepository adminCalendarEventRepository, UserService userService, UserRepository userRepository) {
        this.adminCalendarEventRepository = adminCalendarEventRepository;
        this.userService = userService;
        this.userRepository = userRepository;
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 유저입니다."));
    }

    private static LocalTime sortTime(AdminCalendarEvent e) {
        return e.getTime() == null ? LocalTime.MAX : e.getTime();
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
                        .memo(dto.getMemo())
                        .build()
        );

        return AdminCalendarEventResponseDto.from(saved);
    }

    // 일정 조회
    @Transactional(readOnly = true)
    public List<AdminCalendarEventResponseDto> listEvents() {
        userService.checkAdminAuthority();

        return adminCalendarEventRepository.findAll().stream()
                .sorted(Comparator
                        .comparing(AdminCalendarEvent::getDate)
                        .thenComparing(e -> e.getTime() == null ? LocalTime.MAX : e.getTime()))
                .map(AdminCalendarEventResponseDto::from)
                .toList();
    }

    // 일정 업데이트
    @Transactional
    public AdminCalendarEventResponseDto update(Long id, AdminCalendarEventUpdateDto dto) {
        userService.checkAdminAuthority();

        AdminCalendarEvent adminCalendarEvent = adminCalendarEventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("일정이 존재하지 않습니다."));

        adminCalendarEvent.moveDate(dto.getDate());
        adminCalendarEvent.update(dto.getTime(), dto.getTitle().trim(), dto.getTag(), dto.getMemo());
        return AdminCalendarEventResponseDto.from(adminCalendarEvent);
    }

    // 일정 업데이트(드래그 일정 이동)
    @Transactional
    public AdminCalendarEventResponseDto moveDate(Long id, LocalDate date) {
        userService.checkAdminAuthority();

        AdminCalendarEvent adminCalendarEvent = adminCalendarEventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("일정이 존재하지 않습니다."));

        adminCalendarEvent.moveDate(date);
        return AdminCalendarEventResponseDto.from(adminCalendarEvent);
    }

    @Transactional
    public void delete(Long id) {
        userService.checkAdminAuthority();
        if (!adminCalendarEventRepository.existsById(id)) throw new RuntimeException("일정이 존재하지 않습니다.");
        adminCalendarEventRepository.deleteById(id);
    }
}
