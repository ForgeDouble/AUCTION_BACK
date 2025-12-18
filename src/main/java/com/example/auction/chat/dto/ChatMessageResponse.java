package com.example.auction.chat.dto;

import com.example.auction.chat.domain.ChatFile;
import com.example.auction.chat.domain.ChatMessage;
import com.example.auction.chat.domain.MessageType;
import com.example.auction.user.domain.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    private String id;
    private String roomId;
    private String senderId;
    private String senderNickname;
    private String senderProfileImageUrl;
    private MessageType messageType;
    private String message;
    private List<ChatFileRequest> files;
    private Instant createdAt;

    public static ChatMessageResponse fromEntity(ChatMessage chatMessage, User sender) {
        ChatUserSummary summary = sender != null ? ChatUserSummary.from(sender) : null;
        return fromEntity(chatMessage, summary);
    }

    // 2) 새로 추가: ChatUserSummary 기반 fromEntity
    public static ChatMessageResponse fromEntity(ChatMessage chatMessage, ChatUserSummary sender) {
        List<ChatFileRequest> fileDtos = chatMessage.getFiles().stream()
                .map(ChatFile::fromEntity)
                .toList();

        String nickname = sender != null ? sender.getNickname() : null;
        String profileUrl = sender != null ? sender.getProfileImageUrl() : null;

        return ChatMessageResponse.builder()
                .id(chatMessage.getId())
                .roomId(chatMessage.getRoomId())
                .senderId(chatMessage.getSenderId())
                .senderNickname(nickname)
                .senderProfileImageUrl(profileUrl)
                .messageType(chatMessage.getMessageType())
                .message(chatMessage.getMessage())
                .files(fileDtos)
                .createdAt(chatMessage.getCreatedAt())
                .build();
    }
}
