package com.example.auction.admin.calendar.dto;

import com.example.auction.admin.calendar.domain.CalendarEventTag;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
public class AdminCalendarEventUpdateDto {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime time;

    private String title;

    private CalendarEventTag tag;

    private String memo;
}
