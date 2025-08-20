package com.example.auction.report.dto;

import com.example.auction.report.domain.Report;
import com.example.auction.report.domain.ReportCategory;
import com.example.auction.user.domain.User;
import lombok.*;

@Getter
@Setter
public class ReportCreateDto {
    private Long targetId;
    private ReportCategory category;
    private String content;

}
