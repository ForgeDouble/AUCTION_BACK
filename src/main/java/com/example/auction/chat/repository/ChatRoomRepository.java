package com.example.auction.chat.repository;

import com.example.auction.chat.domain.ChatRoom;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends MongoRepository<ChatRoom, String> {
    Optional<ChatRoom> findByRoomKey(String roomKey);
    List<ChatRoom> findByParticipantIdsContains(String userId);
}
