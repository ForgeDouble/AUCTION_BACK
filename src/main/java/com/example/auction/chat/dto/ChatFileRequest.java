package com.example.auction.chat.dto;

import com.example.auction.chat.domain.ChatFile;
import lombok.Data;

@Data
public class ChatFileRequest {
    private String fileName;
    private String fileUrl;

    public ChatFile toEntity() {
        return ChatFile.builder()
                .fileName(this.fileName)
                .fileUrl(this.fileUrl)
                .build();
    }

}