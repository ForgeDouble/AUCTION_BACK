package com.example.auction.report.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReportProcessDto {
    private Long reportId;
    private boolean accept;
    private String adminNote; // 정지이유
}