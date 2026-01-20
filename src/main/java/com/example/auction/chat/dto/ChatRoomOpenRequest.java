package com.example.auction.chat.dto;

import com.example.auction.chat.domain.ChatRoom;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class ChatRoomOpenRequest {
    private String userId; // 로그인 사용자 식별자
    private String targetId; // 상대방 식별자
    private boolean adminChat; // 운영자 질문방인경우 true 처리용
    private Long productId;

    public ChatRoom toEntityForNormal(String roomKey,
                                      String meEmail,
                                      String targetEmail,
                                      Instant now) {
        return ChatRoom.builder()
                .roomKey(roomKey)
                .participantIds(List.of(meEmail, targetEmail))
                .adminChat(this.adminChat)
                .productId(this.productId)
                .recentTime(now)
                .recentText("방이 생성되었습니다.")
                .build();
    }

    public ChatRoom toEntityForInquiry(String roomKey,
                                       String userEmail,
                                       String inquiryEmail,
                                       Instant now) {
        return ChatRoom.builder()
                .roomKey(roomKey)
                .participantIds(List.of(userEmail, inquiryEmail))
                .adminChat(true)
                .recentTime(now)
                .recentText("문의방이 생성되었습니다.")
                .build();
    }
}