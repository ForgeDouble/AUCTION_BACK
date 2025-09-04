package com.example.auction.report.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminBlockedProductDto {
    private Long productId;
    private String productName;
    private long reportCount;
    private LocalDateTime blockedAt;
    private String blockedReason;
}
