package com.example.auction.report.dto;

import com.example.auction.report.domain.ReportCategory;
import com.example.auction.report.repository.ReportGroupProjection;
import com.example.auction.user.domain.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Optional;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminReportGroupDto {
    private Long targetUserId;
    private String targetName;
    private String targetNickname;
    private ReportCategory category;
    private long pendingCount;
    private long acceptedCount;
    private long rejectedCount;
    private boolean viewOnly;
    private LocalDateTime suspendedUntil;
    private long warning;
    private LocalDateTime lastReportedAt;

    public static AdminReportGroupDto fromEntity(ReportGroupProjection projection, User user) {
        return AdminReportGroupDto.builder()
                .targetUserId(projection.getTargetId())
                .targetName(user != null ? user.getName() : null)
                .targetNickname(user != null ? user.getNickname() : null)
                .category(projection.getCategory())
                .pendingCount(Optional.ofNullable(projection.getPendingCount()).orElse(0L))
                .acceptedCount(Optional.ofNullable(projection.getAcceptedCount()).orElse(0L))
                .rejectedCount(Optional.ofNullable(projection.getRejectedCount()).orElse(0L))
                .viewOnly(user != null && Boolean.TRUE.equals(user.getViewOnly()))
                .suspendedUntil(user != null ? user.getSuspendedUntil() : null)
                .warning(user != null && user.getWarning() != null ? user.getWarning() : 0L)
                .lastReportedAt(projection.getLastReportedAt())
                .build();
    }
}
