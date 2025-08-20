package com.example.auction.report.dto;

import lombok.Data;

@Data
public class AdminResolveDto {
    private boolean accept;
    // 정지 사유
    private String adminContent;
    // 수락 시 정지일수(선택, 없으면 경고만)
    private Long suspendDays;
}