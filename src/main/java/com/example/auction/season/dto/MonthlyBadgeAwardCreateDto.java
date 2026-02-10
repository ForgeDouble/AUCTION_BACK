package com.example.auction.season.dto;

import com.example.auction.season.domain.MonthlyBadgeAward;
import com.example.auction.season.domain.SeasonBadgeType;
import com.example.auction.user.domain.User;

import java.time.Instant;

public record MonthlyBadgeAwardCreateDto(
        String ym,
        SeasonBadgeType badgeType,
        Long userId,
        String nicknameSnapshot,
        String profileImageUrlSnapshot,
        Integer rank,
        Long tagCount,
        Long totalReviews,
        Double ratio,
        Instant createdAt
) {
    public static MonthlyBadgeAwardCreateDto of(
            String ym,
            SeasonBadgeType badgeType,
            Long userId,
            User user,
            Integer rank,
            Long tagCount,
            Long totalReviews,
            Double ratio
    ) {
        String nick = (user.getNickname() != null) ? user.getNickname() : user.getEmail();
        return new MonthlyBadgeAwardCreateDto(
                ym,
                badgeType,
                userId,
                nick,
                user.getProfileImageUrl(),
                rank,
                tagCount,
                totalReviews,
                ratio,
                Instant.now()
        );
    }

    public MonthlyBadgeAward toEntity() {
        return MonthlyBadgeAward.builder()
                .ym(ym)
                .badgeType(badgeType)
                .userId(userId)
                .nicknameSnapshot(nicknameSnapshot)
                .profileImageUrlSnapshot(profileImageUrlSnapshot)
                .rank(rank)
                .tagCount(tagCount)
                .totalReviews(totalReviews)
                .ratio(ratio)
                .createdAt(createdAt)
                .build();
    }


}
