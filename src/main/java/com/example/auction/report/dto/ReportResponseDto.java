package com.example.auction.report.dto;

import com.example.auction.report.domain.Report;
import com.example.auction.report.domain.ReportCategory;
import com.example.auction.report.domain.ReportStatus;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ReportResponseDto {
    private Long id;
    private Long reporterId;
    private String reporterName;
    private Long targetId;
    private ReportCategory category;
    private String content;
    private ReportStatus status;
    private LocalDateTime createdAt;

    public static ReportResponseDto fromEntity(Report report) {
        return ReportResponseDto.builder()
                .id(report.getId())
                .reporterId(report.getReporter().getUserId())
                .reporterName(report.getReporter().getName())
                .targetId(report.getTargetId())
                .category(report.getCategory())
                .content(report.getContent())
                .status(report.getStatus())
                .createdAt(report.getCreatedAt())
                .build();
    }
}