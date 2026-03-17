package com.example.auction.product.search.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ProductSearchIdsPageDto {
    private long total;
    private List<Long> productIds;
}
