package com.example.auction.season.controller;

import com.example.auction.season.service.SeasonMonthlyService;
import lombok.RequiredArgsConstructor;
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
    public String run(@RequestParam String ym,
                      @RequestParam(defaultValue = "true") boolean overwrite) {
        seasonMonthlyService.runForMonth(YearMonth.parse(ym), overwrite);
        return "OK";
    }
}
