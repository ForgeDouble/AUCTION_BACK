package com.example.auction.user.domain;

import com.example.auction.common.domain.BaseTimeEntity;
import com.example.auction.report.domain.ReportCategory;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "user_report_aggregate")
public class UserReportAggregate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)  // 대상 유저
    private Long targetUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReportCategory category;

    @Column(nullable = false)
    private Long pendingCount;

    @Column(nullable = false)
    private Long acceptedCount;
}