package com.example.auction.season.domain;

import com.example.auction.review.domain.ReviewTag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SeasonBadgeType {

    KIND_CERT("친절 인증", ReviewTag.KIND_AND_CONSIDERATE),
    HONEST_CERT("정직 인증", ReviewTag.SAME_AS_DESCRIPTION),
    COMM_CERT("소통 인증", ReviewTag.RESPONDS_WELL),
    DETAIL_CERT("상세 인증", ReviewTag.DETAILED_INFO),
    SPEED_CERT("신속 인증", ReviewTag.FAST_AFTER_WIN);

    private final String label;
    private final ReviewTag reviewTag;
}
