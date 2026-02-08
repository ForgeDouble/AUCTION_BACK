package com.example.auction.season.controller;

import com.example.auction.season.domain.SeasonBadgeType;
import com.example.auction.season.domain.SeasonTitleType;
import com.example.auction.season.repository.MonthlyBadgeAwardRepository;
import com.example.auction.season.repository.MonthlyTitleAwardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/season")
public class SeasonController {

    private final MonthlyTitleAwardRepository monthlyTitleAwardRepository;
    private final MonthlyBadgeAwardRepository monthlyBadgeAwardRepository;

    @GetMapping("/latest")
    public Map<String, Object> latest() {
        YearMonth ym = YearMonth.now(ZoneId.of("Asia/Seoul")).minusMonths(1);
        String key = ym.toString();

        return Map.of(
                "ym", key,
                "titles", monthlyTitleAwardRepository.findByYmOrderByTitleTypeAscRankAsc(key),
                "badges", monthlyBadgeAwardRepository.findByYmOrderByBadgeTypeAscRankAsc(key)
        );
    }

    @GetMapping("/{ym}/titles/{type}")
    public Object titles(@PathVariable String ym, @PathVariable SeasonTitleType seasonTitleType) {
        return monthlyTitleAwardRepository.findByYmAndTitleTypeOrderByRankAsc(ym, seasonTitleType);
    }

    @GetMapping("/{ym}/badges/{type}")
    public Object badges(@PathVariable String ym, @PathVariable SeasonBadgeType seasonBadgeType) {
        return monthlyBadgeAwardRepository.findByYmAndBadgeTypeOrderByRankAsc(ym, seasonBadgeType);
    }
}
