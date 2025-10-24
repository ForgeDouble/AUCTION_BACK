package com.example.auction.product.dto;

import com.example.auction.product.domain.SellYN;
import lombok.*;

// 마이페이지 조회용 Dto
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductWithBidDto {
    private Long productId;
    private String productName;
    private String productContent;
    private Long price;
    private SellYN sellYN;
    private Long bidCount;
    private Long latestBidAmount;
    private String previewImageUrl;
    // Product 엔티티도 함께 포함하고 싶다면
    // private Product product;


}