package com.example.auction.chat.domain;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "chat_room")
public class ChatRoom {
    @Id
    private String id;

    // 1:1 방 고정 키(두 사용자 ID 정렬 -> A_B 형태로 제공
    @Indexed(unique = true)
    private String roomKey;

    //userId/email 문자열 -> 참가자아디
    private List<String> participantIds;

    // 최근 메시지 미리보기 / 시간 -> 채팅방 들어가기 전에 확인가능
    private String recentText;
    private Instant recentTime;

    private boolean adminChat;
    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
