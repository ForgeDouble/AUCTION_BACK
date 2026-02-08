package com.example.auction.season.repository;

import com.example.auction.season.domain.MonthlyBadgeAward;
import com.example.auction.season.domain.SeasonBadgeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonthlyBadgeAwardRepository extends JpaRepository<MonthlyBadgeAward, Long> {

    void deleteByYm(String ym);

    List<MonthlyBadgeAward> findByYmOrderByBadgeTypeAscRankAsc(String ym);

    List<MonthlyBadgeAward> findByYmAndBadgeTypeOrderByRankAsc(String ym, SeasonBadgeType badgeType);

    List<MonthlyBadgeAward> findByUserIdOrderByYmDesc(Long userId);
}
