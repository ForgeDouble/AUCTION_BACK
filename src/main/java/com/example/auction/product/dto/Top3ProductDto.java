package com.example.auction.product.dto;

import com.example.auction.product.domain.Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Top3ProductDto {
    private Long productId;
    private String productName;
    private Status status;
    private LocalDateTime createdAt;
    private Long bidCount;
    private Long latestBidAmount;
    private String previewImageUrl;
    private LocalDateTime auctionEndTime;

    public Top3ProductDto(Long productId, String productName, Status status,
                          Long bidCount,
                          Long latestBidAmount, String previewImageUrl,LocalDateTime createdAt) {
        this.productId = productId;
        this.productName = productName;
        this.status = status;
        this.createdAt = createdAt;
        this.bidCount = bidCount;
        this.latestBidAmount = latestBidAmount;
        this.previewImageUrl = previewImageUrl;
        this.auctionEndTime = createdAt != null ? createdAt.plusMinutes(1440) : null;
    }
}