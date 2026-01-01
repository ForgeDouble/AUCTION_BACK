package com.example.auction.admin.calendar.dto;

import com.example.auction.admin.calendar.domain.AdminCalendarEvent;
import lombok.Builder;
import lombok.Getter;
@Getter
@Builder
public class AdminCalendarEventResponseDto {
    private String id;
    private String date;
    private String time;
    private String title;
    private String tag;
    private String memo;
    private String nickname;

    public static AdminCalendarEventResponseDto from(AdminCalendarEvent e) {
        return AdminCalendarEventResponseDto.builder()
                .id("E-" + e.getId())
                .date(e.getDate() != null ? e.getDate().toString() : null)
                .time(e.getTime() != null ? e.getTime().toString() : null)
                .title(e.getTitle())
                .tag(e.getTag() != null ? e.getTag().getLabel() : "기타")
                .memo(e.getMemo())
                .build();
    }
}
