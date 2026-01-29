package com.example.auction.product.dto;

import com.example.auction.common.validation.annotation.ThousandUnit;
import com.example.auction.product.domain.Product;
import com.example.auction.product.domain.Status;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    @NotNull
	private Long categoryId;
    @NotBlank
    @Size(min = 1, max = 30)
	private String productName;
    @NotBlank
    @Size(min = 1, max = 2500)
    private String productContent;
    @NotNull
    @Min(1000)
    @ThousandUnit
    private Long price;
    @NotBlank
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
