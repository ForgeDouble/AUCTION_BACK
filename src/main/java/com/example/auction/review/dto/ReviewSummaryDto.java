package com.example.auction.review.dto;

import lombok.Builder;
import lombok.Getter;

// rating(평점) 구하기 위한 dto 셋팅
@Getter
@Builder
public class ReviewSummaryDto {
    private double avgRating;
    private long totalCount;
}
