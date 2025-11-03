package com.example.auction.chat.dto;

import com.example.auction.chat.domain.MessageType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class ChatMessageResponse {
    private String id;
    private String roomId;
    private String senderId;
    private MessageType messageType;
    private String message;
    private List<ChatFileRequest> files;
    private Instant createdAt;
}
