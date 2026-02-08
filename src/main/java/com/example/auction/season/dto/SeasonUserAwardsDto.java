package com.example.auction.season.dto;

import java.util.List;

public record SeasonUserAwardsDto(
        String ym,
        List<TitleRow> titles,
        List<BadgeRow> badges
) {
    public record TitleRow(
            String titleType,
            String titleLabel,
            int rank,
            Long metricLong,
            Double metricDouble
    ) {}

    public record BadgeRow(
            String badgeType,
            String badgeLabel,
            int rank,
            Long tagCount,
            Long totalReviews,
            Double ratio
    ) {}


}
