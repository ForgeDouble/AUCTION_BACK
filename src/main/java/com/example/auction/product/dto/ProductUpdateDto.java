package com.example.auction.product.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ProductUpdateDto {
	private Long productId;
	private Long categoryId;
	private String productName;
    private String productContent;
    private Long price;
}
