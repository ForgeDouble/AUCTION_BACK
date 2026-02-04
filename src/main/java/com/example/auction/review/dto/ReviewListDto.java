package com.example.auction.review.dto;

import com.example.auction.review.domain.ReviewTag;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewListDto {
    private Long reviewId;
    private Long productId;
    private String productName;

    private Long reviewerId;
    private String reviewerNick;
    private String reviewerProfileImageUrl;

    private Double rating;
    private List<ReviewTag> tags;

    private String content;
    private String firstImageUrl;
    private LocalDateTime createdAt;
}
