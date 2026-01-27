package com.example.auction.user.dto;

import com.example.auction.admin.calendar.dto.AdminCalendarEventResponseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AdminBirthdayCalendarToggleResDto {
    private boolean enabled;
    private AdminCalendarEventResponseDto event;
}
