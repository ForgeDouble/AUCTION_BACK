package com.example.auction.board.dto;

import com.example.auction.board.domain.Notice;
import com.example.auction.board.domain.NoticeCategory;
import lombok.*;
// 인수인계 게시판(공지) 생성 dto
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NoticeCreateRequest {
    private NoticeCategory category;
    private String title;
    private String content;
    private Boolean pinned;
    private Integer importance;
}
