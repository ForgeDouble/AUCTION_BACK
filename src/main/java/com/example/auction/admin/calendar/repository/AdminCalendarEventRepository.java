package com.example.auction.admin.calendar.repository;

import com.example.auction.admin.calendar.domain.AdminCalendarEvent;
import com.example.auction.admin.calendar.domain.CalendarEventSourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AdminCalendarEventRepository extends JpaRepository<AdminCalendarEvent, Long> {
    List<AdminCalendarEvent> findByDateBetween(LocalDate from, LocalDate to);
    // 생일 이벤트 on 시 등록 레포
    Optional<AdminCalendarEvent> findBySourceTypeAndRefUserIdAndDate(
            CalendarEventSourceType sourceType, Long refUserId, LocalDate date
    );
    // 생일 이벤트 off 시 삭제 레포
    void deleteBySourceTypeAndRefUserId(CalendarEventSourceType sourceType, Long refUserId);
}
