package com.example.auction.product.dto;

import com.example.auction.product.domain.ProductImage;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImageDto {
    private Long id;
    private String url;
    private Integer position;

    public static ProductImageDto from(ProductImage pi) {
        return ProductImageDto.builder()
                .id(pi.getId())
                .url(pi.getUrl())
                .position(pi.getPosition())
                .build();
    }
}