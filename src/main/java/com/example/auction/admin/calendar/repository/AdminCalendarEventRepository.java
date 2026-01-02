package com.example.auction.admin.calendar.repository;

import com.example.auction.admin.calendar.domain.AdminCalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AdminCalendarEventRepository extends JpaRepository<AdminCalendarEvent, Long> {
    List<AdminCalendarEvent> findByDateBetween(LocalDate from, LocalDate to);
}
