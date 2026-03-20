package com.example.auction.product.search.dto;

import com.example.auction.product.domain.Status;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

// 검색 요청 dto

@Getter
@Builder
public class ProductSearchRequest {
    private List<Long> categoryIds;
    private String searchKeyword;
    private Long minPrice;
    private Long maxPrice;
    private List<Status> statuses;
    private String sortBy;
    private int page;
    private int size;
}