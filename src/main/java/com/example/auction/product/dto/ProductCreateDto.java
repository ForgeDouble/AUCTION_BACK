package com.example.auction.product.dto;

import com.example.auction.product.domain.Product;
import com.example.auction.product.domain.Status;

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
public class ProductCreateDto {
	private Long categoryId;
	private String productName;
    private String productContent;
    private Long price;
    private Status status;
    
    public Product toProduct() {
    	return Product.builder()
    			.productName(productName)
    			.productContent(productContent)
    			.price(price)
    			.status(status)
    			.build();
    }
}
