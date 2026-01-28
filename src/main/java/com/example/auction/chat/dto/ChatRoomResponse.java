package com.example.auction.chat.dto;

import com.example.auction.chat.domain.ChatRoom;
import com.example.auction.chat.domain.ChatRoomType;
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
    private String roomKey;
    private String roomType;
    private boolean canRename;

    public static ChatRoomResponse fromEntity(ChatRoom chatRoom, int unread, String roomName) {
        boolean canRename =
                (chatRoom.getRoomType() == ChatRoomType.ADMIN_GROUP || chatRoom.getRoomType() == ChatRoomType.STAFF_GROUP)
                        && !"ADMIN_LOUNGE".equals(chatRoom.getRoomKey());

        return ChatRoomResponse.builder()
                .roomId(chatRoom.getId())
                .participantIds(chatRoom.getParticipantIds())
                .recentText(chatRoom.getRecentText())
                .recentTime(chatRoom.getRecentTime())
                .unread(unread)
                .adminChat(chatRoom.isAdminChat())
                .roomName(roomName)

                .roomKey(chatRoom.getRoomKey())
                .roomType(chatRoom.getRoomType() != null ? chatRoom.getRoomType().name() : null)
                .canRename(canRename)
                .build();
    }
}