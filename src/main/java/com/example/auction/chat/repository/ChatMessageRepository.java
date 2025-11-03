package com.example.auction.chat.repository;

import com.example.auction.chat.domain.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {
    List<ChatMessage> findByRoomIdOrderByCreatedAtDesc(String roomId, Pageable pageable);
}
