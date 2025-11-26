package com.example.auction.chat.dto;

import com.example.auction.chat.domain.ChatRoom;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class ChatRoomResponse {
    private String roomId;
    private List<String> participantIds;
    private String recentText;
    private Instant recentTime;
    private int unread;
    private boolean adminChat;
    private String roomName;

    public static ChatRoomResponse fromEntity(ChatRoom chatRoom, int unread, String roomName) {
        return ChatRoomResponse.builder()
                .roomId(chatRoom.getId())
                .participantIds(chatRoom.getParticipantIds())
                .recentText(chatRoom.getRecentText())
                .recentTime(chatRoom.getRecentTime())
                .unread(unread)
                .adminChat(chatRoom.isAdminChat())
                .roomName(roomName)
                .build();
    }
}