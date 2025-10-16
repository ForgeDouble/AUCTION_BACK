package com.example.auction.product.dto;

import com.example.auction.product.domain.Product;
import com.example.auction.product.domain.ProductImage;
import com.example.auction.product.domain.SellYN;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

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
	private List<ProductImageDto> images;
    
    public static ProductReadAllDto fromEntity(Product product) {
    	return ProductReadAllDto.builder()
    			.productId(product.getProductId())
    			.categoryId(product.getCategory().getCategoryId())
    			.productName(product.getProductName())
    			.productContent(product.getProductContent())
    			.price(product.getPrice())
    			.sellYN(product.getSellYN())
    			.build();
    }
}
