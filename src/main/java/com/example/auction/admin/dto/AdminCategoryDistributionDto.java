package com.example.auction.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AdminCategoryDistributionDto {
    private String category;
    private Long count;
}
