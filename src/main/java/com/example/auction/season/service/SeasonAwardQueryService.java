package com.example.auction.season.service;

import com.example.auction.season.domain.MonthlyBadgeAward;
import com.example.auction.season.domain.MonthlyTitleAward;
import com.example.auction.season.dto.SeasonUserAwardsDto;
import com.example.auction.season.repository.MonthlyBadgeAwardRepository;
import com.example.auction.season.repository.MonthlyTitleAwardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeasonAwardQueryService {

    private final MonthlyTitleAwardRepository monthlyTitleAwardRepository;
    private final MonthlyBadgeAwardRepository monthlyBadgeAwardRepository;

    public SeasonUserAwardsDto getLatestForUser(Long userId) {
        String titleYm = monthlyTitleAwardRepository.findFirstByUserIdOrderByYmDesc(userId)
                .map(MonthlyTitleAward::getYm)
                .orElse(null);

        String badgeYm = monthlyBadgeAwardRepository.findFirstByUserIdOrderByYmDesc(userId)
                .map(MonthlyBadgeAward::getYm)
                .orElse(null);

        String ym = maxYm(titleYm, badgeYm);
        if (ym == null) {
            return new SeasonUserAwardsDto(null, Collections.emptyList(), Collections.emptyList());
        }

        return getForUserByYm(userId, ym);
    }

    public SeasonUserAwardsDto getForUserByYm(Long userId, String ym) {
        List<MonthlyTitleAward> titles = monthlyTitleAwardRepository.findByYmAndUserIdOrderByTitleTypeAscRankAsc(ym, userId);
        List<MonthlyBadgeAward> badges = monthlyBadgeAwardRepository.findByYmAndUserIdOrderByBadgeTypeAscRankAsc(ym, userId);

        List<SeasonUserAwardsDto.TitleRow> titleRows = titles.stream()
                .map(x -> new SeasonUserAwardsDto.TitleRow(
                        x.getTitleType().name(),
                        x.getTitleType().getLabel(),
                        x.getRank(),
                        x.getMetricLong(),
                        x.getMetricDouble()
                ))
                .toList();

        List<SeasonUserAwardsDto.BadgeRow> badgeRows = badges.stream()
                .map(x -> new SeasonUserAwardsDto.BadgeRow(
                        x.getBadgeType().name(),
                        x.getBadgeType().getLabel(),
                        x.getRank(),
                        x.getTagCount(),
                        x.getTotalReviews(),
                        x.getRatio()
                ))
                .toList();

        return new SeasonUserAwardsDto(ym, titleRows, badgeRows);
    }

    private String maxYm(String a, String b) {
        if (a == null && b == null) return null;
        if (a == null) return b;
        if (b == null) return a;

        return (a.compareTo(b) >= 0) ? a : b;
    }


}
