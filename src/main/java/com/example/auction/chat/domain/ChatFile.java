package com.example.auction.chat.domain;

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
}
