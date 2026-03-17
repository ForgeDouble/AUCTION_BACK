package com.example.auction.product.search.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

// 검색 결과
@Getter
@AllArgsConstructor
public class ProductSearchIdsPageDto {
    private long total;
    private List<Long> productIds;
}
