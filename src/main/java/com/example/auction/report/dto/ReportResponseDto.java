package com.example.auction.report.dto;

import com.example.auction.report.domain.ReportCategory;
import com.example.auction.report.domain.ReportStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

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
}