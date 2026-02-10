package com.example.auction.review.dto;

import com.example.auction.review.domain.ReviewTag;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewCreateDto {
    private Long productId;
    private Double rating;
    private List<ReviewTag> tags;
    private String content;
}
