package com.example.auction.product.dto;

import lombok.Data;

import java.util.List;

// imageID 을 보내는 배열
@Data
public class ProductImageOrderRequest {
    private List<Long> imageIds;
}
