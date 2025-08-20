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

    public static UserReportAggregate init(Long targetUserId, ReportCategory category) {
        UserReportAggregate aggregate = new UserReportAggregate();
        aggregate.targetUserId = targetUserId;
        aggregate.category = category;
        aggregate.pendingCount = 0L;
        aggregate.acceptedCount = 0L;
        return aggregate;
    }

    public void increasePending() { this.pendingCount += 1; }

    public void movePendingToAccepted(long n) {
        this.pendingCount = Math.max(0, this.pendingCount - n);
        this.acceptedCount += n;
    }

    public void decreasePending(long n) {
        this.pendingCount = Math.max(0, this.pendingCount - n);
    }
}