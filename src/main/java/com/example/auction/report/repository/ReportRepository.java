package com.example.auction.report.repository;

import com.example.auction.report.domain.Report;
import com.example.auction.report.domain.ReportCategory;
import com.example.auction.report.domain.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {
    boolean existsByReporter_IdAndTargetIdAndCategory(Long reporterId, Long targetId, ReportCategory category);
    List<Report> findByTargetIdAndStatus(Long targetId, ReportStatus status);
}
