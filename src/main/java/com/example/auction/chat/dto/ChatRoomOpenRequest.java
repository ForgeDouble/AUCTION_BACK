package com.example.auction.chat.dto;

import lombok.Data;

@Data
public class ChatRoomOpenRequest {
    private String userId; // 로그인 사용자 식별자
    private String targetId; // 상대방 식별자
    private boolean adminChat; // 운영자 질문방인경우 true 처리용
}