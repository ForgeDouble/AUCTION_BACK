package com.example.auction.report.dto;

import com.example.auction.report.domain.Report;
import com.example.auction.report.domain.ReportCategory;
import com.example.auction.user.domain.User;
import lombok.*;

@Getter
@Setter
public class ReportCreateDto {
    private Long reportedId;
    private ReportCategory category;
    private String content;

    public Report toEntity(User reporter, User reported) {
        return Report.builder()
                .reporter(reporter)
                .reported(reported)
                .category(category)
                .content(content)
                .build();
    }
}
