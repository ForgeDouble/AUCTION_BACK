package com.example.auction.chat.domain;

import com.example.auction.chat.dto.ChatFileRequest;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "chat_file")
public class ChatFile {
    @Id
    private String id;
    private String messageId;
    private String fileName;
    private String fileUrl;

    public static ChatFileRequest fromEntity(ChatFile file) {
        ChatFileRequest chatFileRequest = new ChatFileRequest();
        chatFileRequest.setFileName(file.getFileName());
        chatFileRequest.setFileUrl(file.getFileUrl());
        return chatFileRequest;
    }
}
