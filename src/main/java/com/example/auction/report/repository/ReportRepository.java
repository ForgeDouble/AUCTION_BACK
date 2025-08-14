package com.example.auction.report.repository;

import com.example.auction.report.domain.Report;
import com.example.auction.report.domain.ReportCategory;
import com.example.auction.report.domain.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;

public interface ReportRepository extends JpaRepository<Report, Long> {
    boolean existsByReporter_UserIdAndReported_UserIdAndCategoryAndStatusInAndCreatedAtAfter(
            Long reporterId, Long reportedId, ReportCategory category, Collection<ReportStatus> statuses, LocalDateTime after
    );
}
