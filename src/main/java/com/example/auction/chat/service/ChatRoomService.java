package com.example.auction.chat.service;

import com.example.auction.chat.domain.ChatRoom;
import com.example.auction.chat.dto.ChatRoomOpenRequest;
import com.example.auction.chat.dto.ChatRoomResponse;
import com.example.auction.chat.repository.ChatRoomRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;

    public ChatRoomService(ChatRoomRepository chatRoomRepository) {
        this.chatRoomRepository = chatRoomRepository;
    }

    // 방생성관련 user1 의 userId user2의 userId
    private String keyOf(String a, String b){
        if (a.compareTo(b) <= 0) return a+"_"+b;
        return b+"_"+a;
    }

    public ChatRoom openRoom(ChatRoomOpenRequest request){
        String key = keyOf(request.getUserId(), request.getTargetId());
        return chatRoomRepository.findByRoomKey(key).orElseGet(() -> {
            ChatRoom chatRoom = ChatRoom.builder()
                    .roomKey(key)
                    .participantIds(List.of(request.getUserId(), request.getTargetId()))
                    .adminChat(request.isAdminChat())
                    .recentTime(Instant.now())
                    .recentText("방이 생성되었습니다.")
                    .build();
            return chatRoomRepository.save(chatRoom);
        });
    }


}