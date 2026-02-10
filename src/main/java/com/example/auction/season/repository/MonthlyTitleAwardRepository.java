package com.example.auction.season.repository;

import com.example.auction.season.domain.MonthlyBadgeAward;
import com.example.auction.season.domain.MonthlyTitleAward;
import com.example.auction.season.domain.SeasonBadgeType;
import com.example.auction.season.domain.SeasonTitleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;


public interface MonthlyTitleAwardRepository extends JpaRepository<MonthlyTitleAward, Long> {
    void deleteByYm(String ym);

    List<MonthlyTitleAward> findByYmOrderByTitleTypeAscRankAsc(String ym);

    List<MonthlyTitleAward> findByYmAndTitleTypeOrderByRankAsc(String ym, SeasonTitleType titleType);

    List<MonthlyTitleAward> findByUserIdOrderByYmDesc(Long userId);
    Optional<MonthlyTitleAward> findFirstByUserIdOrderByYmDesc(Long userId);
    List<MonthlyTitleAward> findByYmAndUserIdOrderByTitleTypeAscRankAsc(String ym, Long userId);

}
