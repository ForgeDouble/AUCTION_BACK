package com.example.auction.chat.dto;

import com.example.auction.chat.domain.MessageType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ChatMessageRequest {
    private String roomId;
    private String senderId;
    private MessageType messageType;
    private String message;
    private List<ChatFileRequest> files = new ArrayList<>();
}
