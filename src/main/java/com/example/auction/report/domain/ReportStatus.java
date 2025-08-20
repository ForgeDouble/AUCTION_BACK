package com.example.auction.report.domain;

public enum ReportStatus {
    PENDING,   // 접수
    ACCEPTED,  // 관리자 승인(유효 신고)
    REJECTED   // 관리자 기각(무고/무효)
}
