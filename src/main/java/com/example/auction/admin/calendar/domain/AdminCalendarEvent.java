package com.example.auction.admin.calendar.domain;

import com.example.auction.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "admin_calendar_event",
        indexes = {
                @Index(name = "idx_calendar_event_date", columnList = "event_date")
        })
public class AdminCalendarEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="event_date", nullable = false)
    private LocalDate date;

    @Column(name="event_time")
    private LocalTime time;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CalendarEventTag tag;

    @PrePersist
    void prePersist() {
        if (tag == null) tag = CalendarEventTag.ETC;
    }
}
