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
    private String content;

    private String authorNickname;
    private String createdAt;
    private String updatedAt;

    private boolean acknowledged;
    private NoticeCategory category;
    private int importance;

    public static NoticeResponse fromEntity(Notice notice, boolean acknowledged) {
        return NoticeResponse.builder()
                .id(String.valueOf(notice.getId()))
                .pinned(notice.isPinned())
                .title(notice.getTitle())
                .content(notice.getContent())
                .category(notice.getCategory())
                .authorNickname(notice.getAuthor().getNickname())
                .createdAt(notice.getCreatedAt().toString())
                .updatedAt(notice.getUpdatedAt().toString())
                .acknowledged(acknowledged)
                .importance(notice.getImportance())
                .build();
    }
}
