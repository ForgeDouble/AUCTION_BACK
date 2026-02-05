package com.example.auction.review.dto;

import com.example.auction.review.domain.ReviewTag;
import lombok.*;

import java.util.Map;

@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewSellerSummaryDto {
    private Long sellerId;
    private long reviewCount;
    private double avgRating;
    private Map<ReviewTag, Long> tagCounts;
}
