package com.example.auction.board.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
// 페이징 설정
@Getter
@Builder
public class NoticePageResponse {
    private List<NoticeResponse> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
