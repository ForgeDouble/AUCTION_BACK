package com.example.auction.season.service;

import com.example.auction.common.exception.BadRequestException;
import com.example.auction.common.exception.InternalErrorException;
import com.example.auction.season.domain.MonthlyBadgeAward;
import com.example.auction.season.domain.MonthlyTitleAward;
import com.example.auction.season.dto.SeasonUserAwardsDto;
import com.example.auction.season.repository.MonthlyBadgeAwardRepository;
import com.example.auction.season.repository.MonthlyTitleAwardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class SeasonAwardQueryService {

    private final MonthlyTitleAwardRepository monthlyTitleAwardRepository;
    private final MonthlyBadgeAwardRepository monthlyBadgeAwardRepository;

    public SeasonUserAwardsDto getLatestForUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BadRequestException("USER_ID_REQUIRED", "유효한 userId가 필요합니다.");
        }

        try {
            String titleYm = monthlyTitleAwardRepository.findFirstByUserIdOrderByYmDesc(userId)
                    .map(MonthlyTitleAward::getYm)
                    .orElse(null);

            String badgeYm = monthlyBadgeAwardRepository.findFirstByUserIdOrderByYmDesc(userId)
                    .map(MonthlyBadgeAward::getYm)
                    .orElse(null);

            String ym = maxYmSafe(titleYm, badgeYm);
            if (ym == null) {
                return new SeasonUserAwardsDto(null, Collections.emptyList(), Collections.emptyList());
            }

            validateYmOrThrow(ym);

            return getForUserByYm(userId, ym);

        } catch (BadRequestException e) {
            throw e;
        } catch (InternalErrorException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("[SEASON_AWARD_LATEST_FAILED] userId={}", userId, e);
            throw new InternalErrorException("SEASON_AWARD_QUERY_FAILED", "시즌 수상 조회 중 오류가 발생했습니다.");
        }
    }

    public SeasonUserAwardsDto getForUserByYm(Long userId, String ym) {
        if (userId == null || userId <= 0) {
            throw new BadRequestException("USER_ID_REQUIRED", "유효한 userId가 필요합니다.");
        }
        validateYmOrThrow(ym);

        try {
            List<MonthlyTitleAward> titles =
                    monthlyTitleAwardRepository.findByYmAndUserIdOrderByTitleTypeAscRankAsc(ym, userId);

            List<MonthlyBadgeAward> badges =
                    monthlyBadgeAwardRepository.findByYmAndUserIdOrderByBadgeTypeAscRankAsc(ym, userId);

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

        } catch (BadRequestException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("[SEASON_AWARD_BY_YM_FAILED] userId={} ym={}", userId, ym, e);
            throw new InternalErrorException("SEASON_AWARD_QUERY_FAILED", "시즌 수상 조회 중 오류가 발생했습니다.");
        }
    }

    private void validateYmOrThrow(String ym) {
        if (ym == null || ym.isBlank()) {
            throw new BadRequestException("YM_REQUIRED", "ym(YYYY-MM)이 필요합니다.");
        }
        try {
            YearMonth.parse(ym);
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("YM_INVALID", "ym 형식이 올바르지 않습니다.");
        }
    }

    private String maxYmSafe(String a, String b) {
        if ((a == null || a.isBlank()) && (b == null || b.isBlank())) return null;
        if (a == null || a.isBlank()) {
            validateYmOrStoredOrThrow(b);
            return b;
        }
        if (b == null || b.isBlank()) {
            validateYmOrStoredOrThrow(a);
            return a;
        }

        YearMonth ya = parseStoredYmOrThrow(a);
        YearMonth yb = parseStoredYmOrThrow(b);

        return (ya.compareTo(yb) >= 0) ? a : b;
    }

    private void validateYmOrStoredOrThrow(String ym) {
        parseStoredYmOrThrow(ym);
    }

    private YearMonth parseStoredYmOrThrow(String ym) {
        try {
            return YearMonth.parse(ym);
        } catch (Exception ex) {
            log.error("[SEASON_STORED_YM_INVALID] ym={}", ym, ex);
            throw new InternalErrorException("SEASON_STORED_YM_INVALID", "저장된 시즌 연/월 값이 올바르지 않습니다.");
        }
    }


}
