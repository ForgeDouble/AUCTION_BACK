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
    private Long id;
    private NoticeCategory category;
    private String title;
    private String content;
    private boolean pinned;
    private int importance;

    private Long authorUserId;
    private String authorEmail;
    private String authorNickname;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static NoticeResponse fromEntity(Notice notice) {
        return NoticeResponse.builder()
                .id(notice.getId())
                .category(notice.getCategory())
                .title(notice.getTitle())
                .content(notice.getContent())
                .pinned(notice.isPinned())
                .importance(notice.getImportance())
                .authorUserId(notice.getAuthor().getUserId())
                .authorEmail(notice.getAuthor().getEmail())
                .authorNickname(notice.getAuthor().getNickname())
                .createdAt(notice.getCreatedAt())
                .updatedAt(notice.getUpdatedAt())
                .build();
    }
}
