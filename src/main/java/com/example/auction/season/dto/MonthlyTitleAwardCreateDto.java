package com.example.auction.season.dto;

import com.example.auction.season.domain.MonthlyTitleAward;
import com.example.auction.season.domain.SeasonTitleType;
import com.example.auction.user.domain.User;

import java.time.Instant;

public record MonthlyTitleAwardCreateDto(
        String ym,
        SeasonTitleType titleType,
        Long userId,
        String nicknameSnapshot,
        String profileImageUrlSnapshot,
        Integer rank,
        Long metricLong,
        Double metricDouble,
        Instant createdAt
) {
    public static MonthlyTitleAwardCreateDto of(
            String ym,
            SeasonTitleType titleType,
            Long userId,
            User user,
            Integer rank,
            Long metricLong,
            Double metricDouble
    ) {
        String nick = (user.getNickname() != null) ? user.getNickname() : user.getEmail();
        return new MonthlyTitleAwardCreateDto(
                ym,
                titleType,
                userId,
                nick,
                user.getProfileImageUrl(),
                rank,
                metricLong,
                metricDouble,
                Instant.now()
        );
    }

    public MonthlyTitleAward toEntity() {
        return MonthlyTitleAward.builder()
                .ym(ym)
                .titleType(titleType)
                .userId(userId)
                .nicknameSnapshot(nicknameSnapshot)
                .profileImageUrlSnapshot(profileImageUrlSnapshot)
                .rank(rank)
                .metricLong(metricLong)
                .metricDouble(metricDouble)
                .createdAt(createdAt)
                .build();
    }

}
