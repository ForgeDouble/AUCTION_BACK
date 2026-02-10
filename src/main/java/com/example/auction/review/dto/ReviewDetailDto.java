package com.example.auction.review.dto;

import com.example.auction.review.domain.Review;
import com.example.auction.review.domain.ReviewTag;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewDetailDto {
    private Long reviewId;

    private Long productId;
    private String productName;

    private Long sellerId;
    private String sellerNick;

    private Long reviewerId;
    private String reviewerNick;
    private String reviewerProfileImageUrl;

    private double rating;
    private String content;
    private List<ReviewTag> tags;

    private List<ReviewImageDto> images;

    private LocalDateTime createdAt;

    public static ReviewDetailDto from(Review review, List<ReviewImageDto> images) {
        return ReviewDetailDto.builder()
                .reviewId(review.getReviewId())
                .productId(review.getProduct().getProductId())
                .productName(review.getProduct().getProductName())
                .sellerId(review.getSeller().getUserId())
                .sellerNick(review.getSeller().getNickname())
                .reviewerId(review.getReviewer().getUserId())
                .reviewerNick(review.getReviewer().getNickname())
                .reviewerProfileImageUrl(review.getReviewer().getProfileImageUrl())
                .rating(review.ratingDouble())
                .content(review.getContent())
                .tags(review.getTags() == null ? List.of() : new java.util.ArrayList<>(review.getTags()))
                .images(images)
                .createdAt(review.getCreatedAt())
                .build();
    }
}
