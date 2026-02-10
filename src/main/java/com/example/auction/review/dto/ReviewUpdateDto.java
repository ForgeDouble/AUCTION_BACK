package com.example.auction.review.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewUpdateDto {
    private boolean canWrite;
    private String reason;
}
