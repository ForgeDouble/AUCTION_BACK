package com.example.auction.product.dto;

import com.example.auction.product.domain.Product;
import com.example.auction.product.domain.SellYN;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductReadDto {
	private Long productId;
	private Long categoryId;
	private String productName;
    private String productContent;
    private Long price;
    private SellYN sellYN;
    
    public static ProductReadDto fromEntity(Product product) {
    	return ProductReadDto.builder()
    			.productId(product.getProductId())
    			.categoryId(product.getCategory().getCategoryId())
    			.productName(product.getProductName())
    			.productContent(product.getProductContent())
    			.price(product.getPrice())
    			.sellYN(product.getSellYN())
    			.build();
    }
}
