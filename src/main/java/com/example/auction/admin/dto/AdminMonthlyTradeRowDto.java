package com.example.auction.admin.dto;

// 월별 거래 금액 추이 dto
public record AdminMonthlyTradeRowDto(
        String ym,
        long amount
) {}
