package com.example.auction.user.service;

import com.example.auction.admin.calendar.domain.AdminCalendarEvent;
import com.example.auction.admin.calendar.domain.CalendarEventSourceType;
import com.example.auction.admin.calendar.domain.CalendarEventTag;
import com.example.auction.admin.calendar.dto.AdminCalendarEventResponseDto;
import com.example.auction.admin.calendar.repository.AdminCalendarEventRepository;
import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.BadRequestException;
import com.example.auction.common.exception.InternalErrorException;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.common.exception.UnauthorizedAccessException;
import com.example.auction.user.domain.User;
import com.example.auction.user.dto.AdminBirthdayCalendarToggleResDto;
import com.example.auction.user.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Year;

@Service
public class AdminBirthdayCalendarService {

    private final UserRepository userRepository;
    private final AdminCalendarEventRepository adminCalendarEventRepository;

    public AdminBirthdayCalendarService(UserRepository userRepository,
                                       AdminCalendarEventRepository adminCalendarEventRepository) {
        this.userRepository = userRepository;
        this.adminCalendarEventRepository = adminCalendarEventRepository;
    }

    // user 검증
    private User currentUserOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank() || "anonymousUser".equals(auth.getName())) {
            throw new UnauthorizedAccessException("UNAUTHENTICATED", "로그인이 필요합니다.");
        }

        String email = auth.getName();

        return userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new UnauthorizedAccessException("INVALID_USER", "유효하지 않은 계정입니다."));
    }
    private String displayName(User user) {
        if (user.getNickname() != null && !user.getNickname().isBlank()) return user.getNickname();
        return user.getName();
    }

    private String buildTitle(User user) {
        return "[생일] " + displayName(user);
    }

    private String buildMemo(User u) {
        return "생일 자동 등록 (userId=" + u.getUserId() + ", email=" + u.getEmail() + ", birthday=" + u.getBirthday() + ")";
    }

    private int[] parseMonthDay(String birthday) {
        if (birthday == null || birthday.isBlank()) {
            throw new BadRequestException("BIRTHDAY_REQUIRED", "생일 정보가 없습니다.");
        }
        String raw = birthday.trim();
        String[] parts = raw.split("\\.");
        if (parts.length < 3) {
            throw new BadRequestException("BIRTHDAY_FORMAT_INVALID", "생일 형식이 올바르지 않습니다.");
        }

        try {
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);

            if (month < 1 || month > 12) {
                throw new BadRequestException("BIRTHDAY_FORMAT_INVALID", "생일 월(month)이 올바르지 않습니다.");
            }
            if (day < 1 || day > 31) {
                throw new BadRequestException("BIRTHDAY_FORMAT_INVALID", "생일 일(day)이 올바르지 않습니다.");
            }

            return new int[]{month, day};
        } catch (NumberFormatException e) {
            throw new BadRequestException("BIRTHDAY_FORMAT_INVALID", "생일 형식이 올바르지 않습니다.");
        }
    }

    private LocalDate nextBirthdayDate(LocalDate today, int month, int day) {
        int year = today.getYear();

        int targetDay = day;

        // 정책 - 윤년 일 경우 없다면 2/28
        if (month == 2 && day == 29 && !Year.isLeap(year)) {
            targetDay = 28;
        }

        LocalDate candidate;
        try {
            candidate = LocalDate.of(year, month, targetDay);
        } catch (DateTimeException e) {
            throw new BadRequestException("BIRTHDAY_FORMAT_INVALID", "생일 날짜가 올바르지 않습니다.");
        }

        // 정책 - 오늘로부터 다가오는 1년치 생일만 등록
        if (candidate.isBefore(today)) {
            int ny = year + 1;
            int nd = day;
            if (month == 2 && day == 29 && !Year.isLeap(ny)) nd = 28;

            try {
                candidate = LocalDate.of(ny, month, nd);
            } catch (DateTimeException e) {
                throw new BadRequestException("BIRTHDAY_FORMAT_INVALID", "생일 날짜가 올바르지 않습니다.");
            }
        }

        return candidate;
    }

    @Transactional
    public AdminBirthdayCalendarToggleResDto setEnabled(boolean enabled) {
        User me = currentUserOrThrow();

        try {
            if (enabled) {
                me.enableBirthdayCalendar();
                userRepository.save(me);

                adminCalendarEventRepository.deleteBySourceTypeAndRefUserId(CalendarEventSourceType.BIRTHDAY, me.getUserId());

                int[] md = parseMonthDay(me.getBirthday());
                LocalDate date = nextBirthdayDate(LocalDate.now(), md[0], md[1]);

                AdminCalendarEvent saved = adminCalendarEventRepository.save(
                        AdminCalendarEvent.builder()
                                .date(date)
                                .time(null)
                                .title(buildTitle(me))
                                .tag(CalendarEventTag.OPERATION)
                                .memo(buildMemo(me))
                                .sourceType(CalendarEventSourceType.BIRTHDAY)
                                .refUserId(me.getUserId())
                                .createdByNickname(displayName(me))
                                .build()
                );

                return new AdminBirthdayCalendarToggleResDto(true, AdminCalendarEventResponseDto.from(saved));
            }

            // OFF -> 캘린더에 등록된 나의 생일 삭제
            me.disableBirthdayCalendar();
            userRepository.save(me);

            adminCalendarEventRepository.deleteBySourceTypeAndRefUserId(CalendarEventSourceType.BIRTHDAY, me.getUserId());
            return new AdminBirthdayCalendarToggleResDto(false, null);
        } catch (BadRequestException | UnauthorizedAccessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InternalErrorException("BIRTHDAY_CALENDAR_TOGGLE_FAILED", "생일 캘린더 설정 처리 중 오류가 발생했습니다.");
        }
    }

    @Transactional
    public void syncBirthdayEventIfEnabled(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BadRequestException("USER_ID_REQUIRED", "유효한 userId가 필요합니다.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "유저가 없습니다. userId=" + userId));

        if (!Boolean.TRUE.equals(user.getBirthdayCalendarEnabled())) return;
        try {
            adminCalendarEventRepository.deleteBySourceTypeAndRefUserId(CalendarEventSourceType.BIRTHDAY, user.getUserId());

            int[] md = parseMonthDay(user.getBirthday());
            LocalDate date = nextBirthdayDate(LocalDate.now(), md[0], md[1]);

            adminCalendarEventRepository.save(
                    AdminCalendarEvent.builder()
                            .date(date)
                            .time(null)
                            .title(buildTitle(user))
                            .tag(CalendarEventTag.OPERATION)
                            .memo(buildMemo(user))
                            .sourceType(CalendarEventSourceType.BIRTHDAY)
                            .refUserId(user.getUserId())
                            .createdByNickname(displayName(user))
                            .build()
            );

        } catch (BadRequestException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InternalErrorException("BIRTHDAY_CALENDAR_SYNC_FAILED", "생일 캘린더 동기화 중 오류가 발생했습니다.");
        }
    }
}
