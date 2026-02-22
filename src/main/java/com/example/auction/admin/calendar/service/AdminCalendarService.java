package com.example.auction.admin.calendar.service;

import com.example.auction.admin.calendar.domain.AdminCalendarEvent;
import com.example.auction.admin.calendar.domain.CalendarEventTag;
import com.example.auction.admin.calendar.dto.AdminCalendarEventCreateDto;
import com.example.auction.admin.calendar.dto.AdminCalendarEventResponseDto;
import com.example.auction.admin.calendar.dto.AdminCalendarEventUpdateDto;
import com.example.auction.admin.calendar.repository.AdminCalendarEventRepository;
import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.BadRequestException;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.common.exception.UnauthorizedAccessException;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import com.example.auction.user.service.UserService;
import org.springframework.security.core.Authentication;
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

//    private User currentUser() {
//        String email = SecurityContextHolder.getContext().getAuthentication().getName();
//        return userRepository.findByEmailAndDelYn(email, DelYN.N)
//                .orElseThrow(() -> new RuntimeException("존재하지 않는 유저입니다."));
//    }
//    private void checkAdmin() {
//        String email = SecurityContextHolder.getContext().getAuthentication().getName();
//        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
//                .orElseThrow(() -> new ResourceNotFoundException("로그인중인 User"));
//        if (user.getAuthority() != Authority.ADMIN && user.getAuthority() != Authority.INQUIRY) {
//            throw new UnauthorizedAccessException("관리자 외 권한이 없습니다.");
//        }
//    }

    private String currentEmailOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank() || "anonymousUser".equals(auth.getName())) {
            throw new UnauthorizedAccessException("UNAUTHENTICATED", "로그인이 필요합니다.");
        }
        return auth.getName();
    }
    private User me() {
        String email = currentEmailOrThrow();
        return userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new UnauthorizedAccessException("INVALID_USER", "유효하지 않은 유저입니다. email=" + email));
    }

    private User admin() {
        User user = me();
        if (user.getAuthority() != Authority.ADMIN && user.getAuthority() != Authority.INQUIRY) {
            throw new UnauthorizedAccessException("UNAUTHORIZED_ACCESS", "관리자 외 권한이 없습니다.");
        }
        return user;
    }

    private AdminCalendarEvent eventOrThrow(Long id) {
        if (id == null) {
            throw new BadRequestException("EVENT_ID_REQUIRED", "eventId가 필요합니다.");
        }
        return adminCalendarEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CALENDAR_EVENT_NOT_FOUND", "일정이 존재하지 않습니다. id=" + id));
    }

    private void validateCreate(AdminCalendarEventCreateDto dto) {
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new BadRequestException("TITLE_REQUIRED", "제목은 필수입니다.");
        }
        if (dto.getDate() == null) {
            throw new BadRequestException("DATE_REQUIRED", "날짜 입력은 필수입니다.");
        }
    }

    private void validateUpdate(AdminCalendarEventUpdateDto dto) {
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new BadRequestException("TITLE_REQUIRED", "제목은 필수입니다.");
        }
        if (dto.getDate() == null) {
            throw new BadRequestException("DATE_REQUIRED", "날짜 입력은 필수입니다.");
        }
    }

    private static LocalTime sortTime(AdminCalendarEvent e) {
        return e.getTime() == null ? LocalTime.MAX : e.getTime();
    }
    // 일정 생성
    @Transactional
    public AdminCalendarEventResponseDto create(AdminCalendarEventCreateDto dto) {
//        userService.checkAdminAuthority();
        admin();
        validateCreate(dto);

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
//        userService.checkAdminAuthority();
        admin();
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
//        userService.checkAdminAuthority();
        admin();
        validateUpdate(dto);

        AdminCalendarEvent adminCalendarEvent = eventOrThrow(id);

        adminCalendarEvent.moveDate(dto.getDate());

        CalendarEventTag tag = (dto.getTag() == null) ? CalendarEventTag.ETC : dto.getTag();
        adminCalendarEvent.update(dto.getTime(), dto.getTitle().trim(), dto.getTag(), dto.getMemo());

        return AdminCalendarEventResponseDto.from(adminCalendarEvent);
    }

    // 일정 업데이트(드래그 일정 이동)
    @Transactional
    public AdminCalendarEventResponseDto moveDate(Long id, LocalDate date) {
//        userService.checkAdminAuthority();
        admin();
        if (date == null) {
            throw new BadRequestException("DATE_REQUIRED", "날짜 입력은 필수입니다.");
        }
        AdminCalendarEvent adminCalendarEvent = eventOrThrow(id);
        adminCalendarEvent.moveDate(date);

        return AdminCalendarEventResponseDto.from(adminCalendarEvent);
    }

    @Transactional
    public void delete(Long id) {
//        userService.checkAdminAuthority();
        admin();
        AdminCalendarEvent event = eventOrThrow(id);
        adminCalendarEventRepository.deleteById(id);
    }
}
