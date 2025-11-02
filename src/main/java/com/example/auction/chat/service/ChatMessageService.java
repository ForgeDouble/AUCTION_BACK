package com.example.auction.chat.service;

import com.example.auction.chat.domain.ChatFile;
import com.example.auction.chat.domain.ChatMessage;
import com.example.auction.chat.domain.ChatRoom;
import com.example.auction.chat.domain.MessageType;
import com.example.auction.chat.dto.ChatFileRequest;
import com.example.auction.chat.dto.ChatMessageRequest;
import com.example.auction.chat.dto.ChatMessageResponse;
import com.example.auction.chat.repository.ChatMessageRepository;
import com.example.auction.chat.repository.ChatRoomRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatStateService chatStateService;
    private final SimpMessageSendingOperations messaging;

    public ChatMessageService(ChatMessageRepository chatMessageRepository, ChatRoomRepository chatRoomRepository, ChatStateService chatStateService, SimpMessageSendingOperations messaging) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.chatStateService = chatStateService;
        this.messaging = messaging;
    }

    public List<ChatMessageResponse> getRecent(String roomId, int size){
        return chatMessageRepository
                .findByRoomIdOrderByCreatedAtDesc(roomId, PageRequest.of(0, size))
                .stream().map(this::chatMessageResponse).toList();
    }

    public void send(ChatMessageRequest chatMessageRequest){
        ChatRoom room = chatRoomRepository.findById(chatMessageRequest.getRoomId())
                .orElseThrow(() -> new IllegalArgumentException("방을 찾을 수 없습니다"));

        ChatMessage chatMessage = ChatMessage.builder()
                .roomId(chatMessageRequest.getRoomId())
                .senderId(chatMessageRequest.getSenderId())
                .messageType(chatMessageRequest.getMessageType())
                .message(chatMessageRequest.getMessage())
                .files(chatMessageRequest.getFiles().stream()
                        .map(f -> ChatFile.builder().fileName(f.getFileName()).fileUrl(f.getFileUrl()).build())
                        .toList())
                .build();

        chatMessage = chatMessageRepository.save(chatMessage);

        room.setRecentText(chatMessageRequest.getMessageType() == MessageType.FILE ? "파일을 보냈습니다." : chatMessageRequest.getMessage());
        room.setRecentTime(Instant.now());
        chatRoomRepository.save(room);

        // 상대방 읽음 / 알림 처리
        for (String uid : room.getParticipantIds()){
            if (uid.equals(chatMessageRequest.getSenderId())) continue;
            String cur = chatStateService.currentRoomOf(uid);
            if (cur == null || !cur.equals(room.getId())) {
                int alarm = chatStateService.getAlarm(uid) + 1;
                chatStateService.setAlarm(uid, alarm);
                chatStateService.incUnread(room.getId(), uid);
            }
        }

        ChatMessageResponse chatMessageResponse = chatMessageResponse(chatMessage);
        messaging.convertAndSend("/topic/chat/room/" + chatMessageRequest.getRoomId(), chatMessageResponse);
    }

    private ChatMessageResponse chatMessageResponse(ChatMessage m){
        List<ChatFileRequest> chatFileRequests = m.getFiles().stream().map(file -> {
            ChatFileRequest request = new ChatFileRequest();
            request.setFileName(file.getFileName());
            request.setFileUrl(file.getFileUrl());
            return request;
        }).toList();

        return ChatMessageResponse.builder()
                .id(m.getId())
                .roomId(m.getRoomId())
                .senderId(m.getSenderId())
                .messageType(m.getMessageType())
                .message(m.getMessage())
                .files(chatFileRequests)
                .createdAt(m.getCreatedAt())
                .build();
    }
}
