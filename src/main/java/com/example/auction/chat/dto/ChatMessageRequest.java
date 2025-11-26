package com.example.auction.chat.dto;

import com.example.auction.chat.domain.ChatFile;
import com.example.auction.chat.domain.ChatMessage;
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

    public ChatMessage toEntity(String senderEmail) {
        List<ChatFile> chatFiles = this.files.stream()
                .map(ChatFileRequest::toEntity)
                .toList();

        return ChatMessage.builder()
                .roomId(this.roomId)
                .senderId(senderEmail)
                .messageType(this.messageType)
                .message(this.message)
                .files(chatFiles)
                .build();
    }
}
