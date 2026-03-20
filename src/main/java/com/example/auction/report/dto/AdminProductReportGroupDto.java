package com.example.auction.report.dto;

import com.example.auction.product.domain.Product;
import com.example.auction.report.domain.ReportCategory;
import com.example.auction.report.repository.ReportGroupProjection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Optional;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminProductReportGroupDto {
    private Long productId;
    private String productName;
    private ReportCategory category;

    private long pendingCount;
    private long acceptedCount;
    private long rejectedCount;

    private boolean blocked;
    private LocalDateTime blockedAt;
    private String blockedReason;

    private LocalDateTime lastReportedAt;

    public static AdminProductReportGroupDto fromEntity(ReportGroupProjection projection, Product product) {
        return AdminProductReportGroupDto.builder()
                .productId(projection.getTargetId())
                .productName(product != null ? product.getProductName() : null)
                .category(projection.getCategory())
                .pendingCount(Optional.ofNullable(projection.getPendingCount()).orElse(0L))
                .acceptedCount(Optional.ofNullable(projection.getAcceptedCount()).orElse(0L))
                .rejectedCount(Optional.ofNullable(projection.getRejectedCount()).orElse(0L))
                .blocked(product != null && Boolean.TRUE.equals(product.getBlocked()))
                .blockedAt(product != null ? product.getBlockedAt() : null)
                .blockedReason(product != null ? product.getBlockedReason() : null)
                .lastReportedAt(projection.getLastReportedAt())
                .build();
    }
}