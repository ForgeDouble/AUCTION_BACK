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

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductReadAllDto {
	private Long productId;
	private Long categoryId;
	private String productName;
    private String productContent;
    private Long price;
    private SellYN sellYN;

    private List<CategoryDto> path;
    
    public static ProductReadAllDto fromEntity(Product product) {
        return ProductReadAllDto.builder()
                .productId(product.getProductId())
                .categoryId(product.getCategory().getCategoryId())
                .productName(product.getProductName())
                .productContent(product.getProductContent())
                .price(product.getPrice())
                .sellYN(product.getSellYN())
                .path(product.getCategory().getPath().stream()
                        .map(CategoryDto::fromEntity)
                        .collect(Collectors.toList()))
                .build();
    }
}
