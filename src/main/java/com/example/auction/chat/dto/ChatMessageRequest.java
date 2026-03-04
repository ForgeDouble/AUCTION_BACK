package com.example.auction.chat.dto;

import com.example.auction.chat.domain.ChatFile;
import com.example.auction.chat.domain.ChatMessage;
import com.example.auction.chat.domain.MessageType;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ChatMessageRequest {
    private String roomId;
    private String senderId;
    private MessageType messageType;
    @Size(max = 2000, message = "메시지는 최대 2000자까지 입력할 수 있습니다.")
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
