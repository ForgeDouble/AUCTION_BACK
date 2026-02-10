package com.example.auction.season.domain;

public final class SeasonRules {

    private SeasonRules() {}

    // 탑텐 결정
    public static final int TOP_N = 10;

    // true -> USER만 수상
    // false -> inquiry, admin 추ㅏㄱ
    public static final boolean AWARD_ONLY_USER_AUTHORITY = true;

    // =========================
    //월간 타이틀(~왕) 조건
    // 상단부터
    // 1. 판매왕 최소 판매건수
    // 2. 매출왕 최소 판매 원단위
    // 3. 구매왕 최소 낙찰건수
    // 4. 큰손완 최소 구매 원단위
    // 5. 경매왕 최소 참여 상품 수
    // =========================
    public static final long MIN_SELL_COUNT_FOR_KING = 5;
    public static final long MIN_SELL_GMV_FOR_KING = 300_000;
    public static final long MIN_BUY_COUNT_FOR_KING = 3;
    public static final long MIN_BUY_GMV_FOR_KING = 300_000;
    public static final long MIN_AUCTION_PARTICIPATION_FOR_KING = 10;

    // =========================
    // 저격왕(승률) 규칙
    // =========================
    public static final long SNIPER_MIN_PARTICIPATED = 10; // 참여 상품 수 최소
    public static final long SNIPER_MIN_WINS = 2; // 낙찰 수 최소

    // =========================
    // 월간 인증(리뷰 태그) 최소 조건
    // =========================
    public static final long MIN_REVIEWS_FOR_BADGE = 8; // 판매자 월간 총 리뷰 최소
    public static final long MIN_TAG_COUNT_FOR_BADGE = 3; // 해당 태그 최소
    public static final double MIN_TAG_RATIO_FOR_BADGE = 0.35; // 태그 비율 최소


}
