package com.example.auction.report.dto;

import com.example.auction.product.domain.Product;
import com.example.auction.report.domain.Report;
import com.example.auction.report.domain.ReportCategory;
import com.example.auction.user.domain.User;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductReportCreateDto {
    private Long productId;
    private ReportCategory category;
    private String content;

}
