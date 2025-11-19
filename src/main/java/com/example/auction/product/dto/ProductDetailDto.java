package com.example.auction.product.dto;

import com.example.auction.product.domain.Product;
import com.example.auction.product.domain.Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/* 상품 상세 조회 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDetailDto {
	private Long productId;
	private Long categoryId;
	private String productName;
    private String productContent;
    private Long price;
    private Status status;
	private List<ProductImageDto> images;
    
    public static ProductDetailDto fromEntity(Product product) {
    	return ProductDetailDto.builder()
    			.productId(product.getProductId())
    			.categoryId(product.getCategory().getCategoryId())
    			.productName(product.getProductName())
    			.productContent(product.getProductContent())
    			.price(product.getPrice())
    			.status(product.getStatus())
    			.build();
    }


}
