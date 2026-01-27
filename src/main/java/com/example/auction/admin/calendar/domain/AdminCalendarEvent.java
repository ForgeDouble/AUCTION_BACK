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

    @Column(length = 2000)
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    @Builder.Default
    private CalendarEventSourceType sourceType = CalendarEventSourceType.MANUAL;

    @Column(name = "ref_user_id")
    private Long refUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CalendarEventTag tag;

    @PrePersist
    void prePersist() {
        if (tag == null) tag = CalendarEventTag.ETC;
    }

    public void update(LocalTime time, String title, CalendarEventTag tag, String memo) {
        this.time = time;
        this.title = title;
        this.tag = (tag == null ? CalendarEventTag.ETC : tag);
        this.memo = memo;
    }

    public void moveDate(LocalDate date) {
        this.date = date;
    }

    public void markBirthday(Long userId) {
        this.sourceType = CalendarEventSourceType.BIRTHDAY;
        this.refUserId = userId;
    }
}
