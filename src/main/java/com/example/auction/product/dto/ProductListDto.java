package com.example.auction.product.dto;

import com.example.auction.category.domain.Category;
import com.example.auction.category.dto.CategoryDto;
import com.example.auction.product.domain.Product;
import com.example.auction.product.domain.SellYN;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* 목록 리스트 보기 */

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductListDto {
	private Long productId;
    private String productName;
    private String productContent;
    private Long price;
    private SellYN sellYN;
	private String previewImageUrl;
    private Long categoryId;
    private List<CategoryDto> path;
    private String userEmail;
    private Long latestBidAmount;
    private Long bidCount;

    public static ProductListDto fromEntity(Product product) {
        return ProductListDto.builder()
                .productId(product.getProductId())
                .categoryId(product.getCategory().getCategoryId())
                .userEmail(product.getUser().getEmail())
                .productName(product.getProductName())
                .productContent(product.getProductContent())
                .price(product.getPrice())
                .sellYN(product.getSellYN())
                .path(product.getCategory().getPath().stream()
                        .map(CategoryDto::fromEntity)
                        .collect(Collectors.toList()))
                .build();
    }
    public ProductListDto(Long productId, String productName, String productContent,
                          Long price, SellYN sellYN, String previewImageUrl,
                          Long categoryId, String userEmail, Long latestBidAmount, Long bidCount) {
        this.productId = productId;
        this.productName = productName;
        this.productContent = productContent;
        this.price = price;
        this.sellYN = sellYN;
        this.previewImageUrl = previewImageUrl;
        this.categoryId = categoryId;
        this.userEmail = userEmail;
        this.latestBidAmount = latestBidAmount;
        this.bidCount = bidCount;
    }
}
