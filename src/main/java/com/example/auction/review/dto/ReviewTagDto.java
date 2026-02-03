package com.example.auction.review.dto;

import com.example.auction.review.domain.ReviewTag;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewTagDto {
    private String code;
    private String label;

    public static ReviewTagDto from(ReviewTag tag) {
        return ReviewTagDto.builder()
                .code(tag.name())
                .label(tag.getLabel())
                .build();
    }
}
