package com.example.auction.admin.calendar.dto;

import com.example.auction.admin.calendar.domain.CalendarEventTag;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
@Getter
@Setter
public class AdminCalendarEventCreateDto {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime time;

    private String title;

    private CalendarEventTag tag;
}
