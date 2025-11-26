package com.example.auction.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

// 일일 통계 관련 dto
@Getter
@AllArgsConstructor
@Builder
public class DailyActiveUserStatsDto {

    private String date;
    private long count;
    private List<String> emails;

    public static DailyActiveUserStatsDto dailyActiveUserStatsDto(LocalDate date, List<String> emails) {
        List<String> safe = (emails != null) ? emails : Collections.emptyList();
        return DailyActiveUserStatsDto.builder()
                .date(date.toString())
                .count(safe.size())
                .emails(safe)
                .build();
    }


}