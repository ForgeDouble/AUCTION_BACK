package com.example.auction.chat.domain;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
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

    @Indexed(unique = true)
    private String roomKey;

//    //userId/email 문자열 -> 참가자아디
//    private List<String> participantIds;

    // 참가자: email 문자열
    @Builder.Default
    private List<String> participantIds = new ArrayList<>();

    // 방 제목(그룹/라운지용)
    private String title;

    @Builder.Default
    private ChatRoomType roomType = ChatRoomType.NORMAL;

    // 최근 메시지 미리보기 / 시간 -> 채팅방 들어가기 전에 확인가능
    private String recentText;
    private Instant recentTime;

    private boolean adminChat;
    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public void updateRecent(String text, Instant time) {
        this.recentText = text;
        this.recentTime = time;
    }

    public boolean isStaffRoom() {
        return roomType == ChatRoomType.ADMIN_GROUP || roomType == ChatRoomType.STAFF_GROUP || roomType == ChatRoomType.INQUIRY;
    }
}
