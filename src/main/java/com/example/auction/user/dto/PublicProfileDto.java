package com.example.auction.user.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicProfileDto {
    private Long userId;
    private String nickname;
    private String profileImageUrl;
    private LocalDateTime createdAt;

    private long tradeCount;

    private long totalProducts;
    private long sellingProducts;
    private long endedProducts;

    // 본인 신고 방지용
    private boolean reportable;

}