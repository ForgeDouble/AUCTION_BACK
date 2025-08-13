package com.example.auction.report.dto;

import com.example.auction.report.domain.ReportCategory;
import lombok.*;

@Getter
@Setter
public class ReportCreateDto {
    private Long reportedUserId;
    private ReportCategory category;
    private String description;
}
