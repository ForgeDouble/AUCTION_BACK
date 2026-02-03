package com.example.auction.review.domain;

import lombok.Getter;

@Getter
public enum ReviewTag {
    MATCH_DESCRIPTION("상품 설명과 실제 상품이 동일해요"),
    KIND_CONSIDERATE("친절하고 배려가 넘처요"),
    INQUIRY_REPLY("상품 문의에 대한 답변이 성실해요"),
    DETAIL_INFO("상품 정보가 자세히 적혀있어요"),
    FAST_AFTER_WIN("낙찰 후 빠른 조치가 이루어저요");

    private final String label;

    ReviewTag(String label) {
        this.label = label;
    }

}
