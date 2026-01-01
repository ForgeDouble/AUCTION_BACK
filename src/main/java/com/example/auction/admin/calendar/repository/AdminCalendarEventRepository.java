package com.example.auction.admin.calendar.repository;

import com.example.auction.admin.calendar.domain.AdminCalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminCalendarEventRepository extends JpaRepository<AdminCalendarEvent, Long> {
}
