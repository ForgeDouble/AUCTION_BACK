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

import java.time.LocalDateTime;
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
    private Long wishlistCount;
    private LocalDateTime createdAt;
    private LocalDateTime auctionStartTime;
    private LocalDateTime auctionEndTime;
    private Long wishlistId;

    public static ProductListDto fromEntity(Product product) {
        ProductListDto dto = ProductListDto.builder()
                .productId(product.getProductId())
                .categoryId(product.getCategory().getCategoryId())
                .userEmail(product.getUser().getEmail())
                .productName(product.getProductName())
                .productContent(product.getProductContent())
                .price(product.getPrice())
                .status(product.getStatus())
                .createdAt(product.getCreatedAt())
                .auctionStartTime(product.getAuctionStartTime())
                .auctionEndTime(product.getAuctionEndTime())
//                .previewImageUrl(builder().previewImageUrl)
                .createdAt(product.getCreatedAt())
                .path(product.getCategory().getPath().stream()
                        .map(CategoryBasicDto::fromEntity)
                        .collect(Collectors.toList()))
                .build();

        if (dto.getCreatedAt() != null) {
            dto.setAuctionEndTime(dto.getCreatedAt().plusMinutes(1440));
        }
        return dto;
    }
    public ProductListDto(Long productId, String productName, String productContent,
                          Long price, Status status, String imageUrl,
                          Long categoryId, String email, Long currentBidAmount,
                          Long bidCount, Long wishlistCount, LocalDateTime createdAt) {
        this.productId = productId;
        this.productName = productName;
        this.productContent = productContent;
        this.price = price;
        this.status = status;
        this.previewImageUrl = imageUrl;
        this.categoryId = categoryId;
        this.userEmail = email;
        this.latestBidAmount = currentBidAmount;
        this.bidCount = bidCount;
        this.wishlistCount = wishlistCount;
        this.createdAt = createdAt;
        this.auctionEndTime = createdAt != null ? createdAt.plusMinutes(1440) : null;
    }
}
