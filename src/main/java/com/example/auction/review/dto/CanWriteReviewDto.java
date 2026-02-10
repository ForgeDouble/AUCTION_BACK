package com.example.auction.review.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CanWriteReviewDto {
    private boolean canWrite;
    private String reason;
}
