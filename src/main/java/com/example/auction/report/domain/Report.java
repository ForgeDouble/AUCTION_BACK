package com.example.auction.report.domain;

import com.example.auction.common.domain.BaseTimeEntity;
import com.example.auction.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Report extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 신고한사람
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    // 신고당한 사람
    @Column(nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportCategory category;

    // 신고 이유
    @Column(length = 500)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status;

    private LocalDateTime processedAt;
    @Column(length = 500)
    private String adminContent; // 정지/경고 이유


    @PrePersist
    void prePersist() {
        if (status == null) status = ReportStatus.PENDING;
    }

    public static Report create(User reporter, Long targetId, ReportCategory category, String content) {
        Report report = new Report();
        report.reporter = reporter;
        report.targetId = targetId;
        report.category = category;
        report.content  = content;
        report.status   = ReportStatus.PENDING;
        return report;
    }

    public void accept(String adminContent) {
        this.status = ReportStatus.ACCEPTED;
        this.processedAt = LocalDateTime.now();
        this.adminContent = adminContent;
    }
    public void reject(String adminContent) {
        this.status = ReportStatus.REJECTED;
        this.processedAt = LocalDateTime.now();
        this.adminContent = adminContent;
    }
}
