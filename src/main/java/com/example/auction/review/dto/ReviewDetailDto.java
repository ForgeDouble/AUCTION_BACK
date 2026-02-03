package com.example.auction.review.dto;

import com.example.auction.review.domain.Review;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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
    private String sellerNickname;
    private String sellerProfileImageUrl;

    private Long reviewerId;
    private String reviewerNickname;
    private String reviewerProfileImageUrl;

    private String content;
    private List<ReviewTagDto> tags;
    private List<ReviewImageDto> images;
    private LocalDateTime createdAt;

    public static ReviewDetailDto from(Review review, List<ReviewImageDto> images) {

        List<ReviewTagDto> tagDtos = review.getTags().stream()
                .map(ReviewTagDto::from)
                .collect(Collectors.toList());

        return ReviewDetailDto.builder()
                .reviewId(review.getReviewId())
                .productId(review.getProduct().getProductId())
                .productName(review.getProduct().getProductName())
                .sellerId(review.getSeller().getUserId())
                .sellerNickname(review.getSeller().getNickname())
                .sellerProfileImageUrl(review.getSeller().getProfileImageUrl())
                .reviewerId(review.getReviewer().getUserId())
                .reviewerNickname(review.getReviewer().getNickname())
                .reviewerProfileImageUrl(review.getReviewer().getProfileImageUrl())
                .content(review.getContent())
                .tags(tagDtos)
                .images(images)
                .createdAt(review.getCreatedAt())
                .build();
    }


}
