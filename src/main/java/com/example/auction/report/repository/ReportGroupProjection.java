package com.example.auction.report.repository;

import com.example.auction.report.domain.ReportCategory;

import java.time.LocalDateTime;

public interface ReportGroupProjection {
    Long getTargetId();
    ReportCategory getCategory();
    Long getPendingCount();
    Long getAcceptedCount();
    Long getRejectedCount();
    LocalDateTime getLastReportedAt();
}
