package com.example.auction.chat.dto;

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
}