package com.example.auction.admin.calendar.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
public class AdminCalendarEventDateUpdateDto {
    // 드래그 작업으로 update 위한 dto

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;
}
