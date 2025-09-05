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
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ReportTargetType targetType;

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

    public static Report create(User reporter, ReportTargetType targetType, Long targetId,
                                ReportCategory category, String content) {
        return Report.builder()
                .reporter(reporter)
                .targetType(targetType)
                .targetId(targetId)
                .category(category)
                .content(content)
                .status(ReportStatus.PENDING)
                .build();
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
