package com.example.auction.season.controller;

import com.example.auction.common.dto.CommonResDto;
import com.example.auction.season.service.SeasonMonthlyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/season")
public class AdminSeasonController {

    private final SeasonMonthlyService seasonMonthlyService;

    @PostMapping("/run")
    public ResponseEntity<CommonResDto> run(
            @RequestParam String ym,
            @RequestParam(defaultValue = "true") boolean overwrite
    ) {
        seasonMonthlyService.runForMonth(YearMonth.parse(ym), overwrite);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "시즌 집계 실행 OK", null));
    }


}
