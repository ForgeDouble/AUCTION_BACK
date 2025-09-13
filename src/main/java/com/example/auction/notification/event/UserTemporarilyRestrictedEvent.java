package com.example.auction.notification.event;

import com.example.auction.report.domain.ReportCategory;

public record UserTemporarilyRestrictedEvent(
        Long targetUserId,
        ReportCategory category,
        Long categoryPendingCount,
        String reason
) {}
