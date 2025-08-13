package com.example.auction.report.domain;

import com.example.auction.common.domain.BaseTimeEntity;
import com.example.auction.user.domain.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
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
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_id", nullable = false)
    private User reported;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private ReportCategory category;

    @Column(length = 500)
    private String description; // 증빙/사유(선택, ETC면 필수 추천)

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private ReportStatus status;

    // 조치 시간
    private LocalDateTime processedAt;

}
