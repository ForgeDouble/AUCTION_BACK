package com.example.auction.season.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SeasonTitleType {

    SELL_COUNT_KING("판매왕"),
    SELL_GMV_KING("매출왕"),
    BUY_COUNT_KING("구매왕"),
    BUY_GMV_KING("큰손왕"),
    AUCTION_KING("경매왕"),
    SNIPER_KING("저격왕");

    private final String label;
}
