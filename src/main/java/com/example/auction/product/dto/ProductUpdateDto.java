package com.example.auction.product.dto;

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
public class ProductUpdateDto {
	private Long productId;
	private Long categoryId;
	private String productName;
    private String productContent;
    private Long price;
}
