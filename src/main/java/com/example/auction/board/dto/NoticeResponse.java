package com.example.auction.board.dto;

import com.example.auction.board.domain.Notice;
import com.example.auction.board.domain.NoticeCategory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// 인수인계 게시판(공지) 조회 dto
@Getter
@Builder
public class NoticeResponse {

    private String id;
    private boolean pinned;

    private String title;
    private String body;

    private String author;
    private String createdAt;

    private boolean acknowledged;

    public static NoticeResponse fromEntity(Notice notice, boolean acknowledged) {
        return NoticeResponse.builder()
                .id(String.valueOf(notice.getId()))
                .pinned(notice.isPinned())
                .title(notice.getTitle())
                .body(notice.getContent())
                .author(notice.getAuthor().getNickname())
                .createdAt(notice.getCreatedAt().toString())
                .acknowledged(acknowledged)
                .build();
    }
}
