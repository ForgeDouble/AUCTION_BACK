package com.example.auction.report.domain;

public enum ReportStatus {
    PENDING, // 접수됨
    AUTO_CONFIRMED, // 자동 경고 처리
    RESOLVED, // 관리자 직접 처리
    REJECTED, // 기각
}
