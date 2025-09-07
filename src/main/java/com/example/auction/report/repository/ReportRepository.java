package com.example.auction.report.repository;

import com.example.auction.report.domain.Report;
import com.example.auction.report.domain.ReportCategory;
import com.example.auction.report.domain.ReportStatus;
import com.example.auction.report.domain.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporter_UserIdAndTargetIdAndCategory(Long reporterUserId, Long targetId, ReportCategory category);


    List<Report> findByTargetIdAndCategoryAndStatus(Long targetId, ReportCategory category, ReportStatus status);

    // 관리자 그룹 요약 집계
    @Query("""
        select 
          r.targetId as targetId,
          r.category as category,
          sum(case when r.status = com.example.auction.report.domain.ReportStatus.PENDING then 1 else 0 end) as pendingCount,
          sum(case when r.status = com.example.auction.report.domain.ReportStatus.ACCEPTED then 1 else 0 end) as acceptedCount,
          sum(case when r.status = com.example.auction.report.domain.ReportStatus.REJECTED then 1 else 0 end) as rejectedCount,
          max(r.createdAt) as lastReportedAt
        from Report r
        where r.targetType = :targetType
        group by r.targetId, r.category
        """)
    List<ReportGroupProjection> aggregateReportGroupsByTargetType(@Param("targetType") ReportTargetType targetType);

    // 그룹 상세 페이징
//    Page<Report> findByTargetIdAndCategory(Long targetId, ReportCategory category, Pageable pageable);
    Page<Report> findByTargetTypeAndTargetIdAndCategory(
            ReportTargetType targetType, Long targetId, ReportCategory category, Pageable pageable);

    boolean existsByReporter_UserIdAndTargetTypeAndTargetId(
            Long reporterUserId, ReportTargetType targetType, Long targetId);
}