package com.example.auction.review.dto;

import com.example.auction.review.domain.ReviewImage;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewImageDto {
    private Long id;
    private String url;
    private Integer position;

    public static ReviewImageDto from(ReviewImage image) {
        return ReviewImageDto.builder()
                .id(image.getId())
                .url(image.getUrl())
                .position(image.getPosition())
                .build();
    }
}
