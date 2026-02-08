package com.example.auction.season.controller;

import com.example.auction.common.dto.CommonResDto;
import com.example.auction.season.domain.SeasonBadgeType;
import com.example.auction.season.domain.SeasonTitleType;
import com.example.auction.season.dto.SeasonUserAwardsDto;
import com.example.auction.season.repository.MonthlyBadgeAwardRepository;
import com.example.auction.season.repository.MonthlyTitleAwardRepository;
import com.example.auction.season.service.SeasonAwardQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/season")
public class SeasonController {

    private final MonthlyTitleAwardRepository monthlyTitleAwardRepository;
    private final MonthlyBadgeAwardRepository monthlyBadgeAwardRepository;
    private final SeasonAwardQueryService seasonAwardQueryService;

    @GetMapping("/latest")
    public ResponseEntity<CommonResDto> latestAll() {
        YearMonth ym = YearMonth.now(ZoneId.of("Asia/Seoul")).minusMonths(1);
        String key = ym.toString();

        Map<String, Object> payload = Map.of(
                "ym", key,
                "titles", monthlyTitleAwardRepository.findByYmOrderByTitleTypeAscRankAsc(key),
                "badges", monthlyBadgeAwardRepository.findByYmOrderByBadgeTypeAscRankAsc(key)
        );

        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "시즌 최신 랭킹 조회 성공", payload));
    }

    @GetMapping("/{ym}/titles/{type}")
    public ResponseEntity<CommonResDto> titles(
            @PathVariable String ym,
            @PathVariable("type") SeasonTitleType type
    ) {
        return ResponseEntity.ok(new CommonResDto(
                HttpStatus.OK,
                "칭호 랭킹 조회 성공",
                monthlyTitleAwardRepository.findByYmAndTitleTypeOrderByRankAsc(ym, type)
        ));
    }

    @GetMapping("/{ym}/badges/{type}")
    public ResponseEntity<CommonResDto> badges(
            @PathVariable String ym,
            @PathVariable("type") SeasonBadgeType type
    ) {
        return ResponseEntity.ok(new CommonResDto(
                HttpStatus.OK,
                "인증 뱃지 랭킹 조회 성공",
                monthlyBadgeAwardRepository.findByYmAndBadgeTypeOrderByRankAsc(ym, type)
        ));
    }

    // 유저 최신 월 칭호/인증 뱃지
    @GetMapping("/user/{userId}/latest")
    public ResponseEntity<CommonResDto> latestForUser(@PathVariable Long userId) {
        SeasonUserAwardsDto dto = seasonAwardQueryService.getLatestForUser(userId);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "유저 최신 시즌 수상 조회 성공", dto));
    }

    // 유저 특정 월 조회
    @GetMapping("/user/{userId}")
    public ResponseEntity<CommonResDto> byYm(
            @PathVariable Long userId,
            @RequestParam String ym
    ) {
        SeasonUserAwardsDto dto = seasonAwardQueryService.getForUserByYm(userId, ym);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "유저 시즌 수상 조회 성공", dto));
    }
}
