package com.example.auction.report.dto;

import com.example.auction.report.domain.Report;
import com.example.auction.report.domain.ReportStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminReportItemDto {
    private Long id;
    private ReportStatus status;

    private String content;

    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private String adminContent;

    private Long reporterId;
    private String reporterName;
    private String reporterNickname;

    public static AdminReportItemDto fromEntity(Report r) {
        return AdminReportItemDto.builder()
                .id(r.getId())
                .status(r.getStatus())
                .content(r.getContent())
                .createdAt(r.getCreatedAt())
                .processedAt(r.getProcessedAt())
                .adminContent(r.getAdminContent())
                .reporterId(r.getReporter() != null ? r.getReporter().getUserId() : null)
                .reporterName(r.getReporter() != null ? r.getReporter().getName() : null)
                .reporterNickname(r.getReporter() != null ? r.getReporter().getNickname() : null)
                .build();
    }
}
