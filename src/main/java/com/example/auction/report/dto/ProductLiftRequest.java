package com.example.auction.report.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductLiftRequest {
    private Long productId;
    private String reason;
    private Boolean resetCounter;
}
