package com.example.auction.report.dto;

import lombok.Data;

@Data
public class AdminResolveDto {
    private boolean accept;
    private String adminContent;    // 정지 사유
    private Long suspendDays;   // 수락 시 정지일수(선택, 없으면 경고만)
}