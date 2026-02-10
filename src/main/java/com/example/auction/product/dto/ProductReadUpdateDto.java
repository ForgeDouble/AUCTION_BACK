package com.example.auction.product.dto;

import com.example.auction.product.domain.Product;
import com.example.auction.product.domain.Status;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductReadUpdateDto {
    private Long productId;
    private Long userId;
    private Long categoryId;
    private String productName;
    private String productContent;
    private Long price;
    private Status status;
    private List<ProductImageDto> images;
    private LocalDateTime createdAt;

    public static ProductReadUpdateDto fromEntity(Product product) {
        return ProductReadUpdateDto.builder()
                .productId(product.getProductId())
                .userId(product.getUser().getUserId())
                .categoryId(product.getCategory().getCategoryId())
                .productName(product.getProductName())
                .productContent(product.getProductContent())
                .price(product.getPrice())
                .status(product.getStatus())
                .createdAt(product.getCreatedAt())
                .build();
    }
}
