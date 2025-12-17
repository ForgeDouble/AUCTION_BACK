package com.example.auction.product.dto;

import com.example.auction.category.dto.CategoryBasicDto;
import com.example.auction.product.domain.Product;
import com.example.auction.product.domain.Status;
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
    private Status status;
	private String previewImageUrl;
    private Long categoryId;
    private List<CategoryBasicDto> path;
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
                .status(product.getStatus())
                .path(product.getCategory().getPath().stream()
                        .map(CategoryBasicDto::fromEntity)
                        .collect(Collectors.toList()))
                .build();
    }
    public ProductListDto(Long productId, String productName, String productContent,
                          Long price, Status status, String previewImageUrl,
                          Long categoryId, String userEmail, Long latestBidAmount, Long bidCount) {
        this.productId = productId;
        this.productName = productName;
        this.productContent = productContent;
        this.price = price;
        this.status = status;
        this.previewImageUrl = previewImageUrl;
        this.categoryId = categoryId;
        this.userEmail = userEmail;
        this.latestBidAmount = latestBidAmount;
        this.bidCount = bidCount;
    }
}
