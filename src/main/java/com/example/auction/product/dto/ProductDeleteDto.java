package com.example.auction.product.dto;

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
public class ProductDeleteDto {
	private Long productId;
	
}
