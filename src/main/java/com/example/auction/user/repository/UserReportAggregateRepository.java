package com.example.auction.user.repository;

import com.example.auction.report.domain.ReportCategory;
import com.example.auction.user.domain.UserReportAggregate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserReportAggregateRepository extends JpaRepository<UserReportAggregate, Long> {
    Optional<UserReportAggregate> findByTargetUserIdAndCategory(Long targetUserId, ReportCategory category);
    List<UserReportAggregate> findAllByTargetUserId(Long targetUserId);
}