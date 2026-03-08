package com.example.auction.product.dto;

import com.example.auction.product.domain.Status;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProductListPageRowDto {
    private Long productId;
    private String productName;
    private String productContent;
    private Long price;
    private Status status;
    private Long categoryId;
    private String userEmail;
    private LocalDateTime createdAt;

    public ProductListDto toList() {
        ProductListDto dto = ProductListDto.builder()
                .productId(productId)
                .productName(productName)
                .productContent(productContent)
                .price(price)
                .status(status)
                .categoryId(categoryId)
                .userEmail(userEmail)
                .createdAt(createdAt)
                .previewImageUrl(null)
                .latestBidAmount(0L)
                .bidCount(0L)
                .wishlistCount(0L)
                .build();

        if (createdAt != null) {
            dto.setAuctionStartTime(createdAt.plusMinutes(2));
            dto.setAuctionEndTime(createdAt.plusMinutes(30));
        }

        return dto;
    }
}