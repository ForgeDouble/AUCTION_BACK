package com.example.auction.chat.service;

import com.example.auction.chat.domain.ChatFile;
import com.example.auction.chat.domain.ChatMessage;
import com.example.auction.chat.domain.ChatRoom;
import com.example.auction.chat.dto.ChatFileRequest;
import com.example.auction.chat.dto.ChatMessageRequest;
import com.example.auction.chat.dto.ChatMessageResponse;
import com.example.auction.chat.repository.ChatMessageRepository;
import com.example.auction.chat.repository.ChatRoomRepository;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
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
    @Qualifier("chatRoom")
    private final RedisTemplate<String, Object> redisTemplate;
    @Qualifier("chat")
    private final ChannelTopic chatTopic;
    private final UserRepository userRepository;

    public ChatMessageService(ChatMessageRepository chatMessageRepository, ChatRoomRepository chatRoomRepository, ChatStateService chatStateService, SimpMessageSendingOperations messaging, @Qualifier("chatRoom") RedisTemplate<String, Object> redisTemplate, @Qualifier("chat") ChannelTopic chatTopic, UserRepository userRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.chatStateService = chatStateService;
        this.messaging = messaging;
        this.redisTemplate = redisTemplate;
        this.chatTopic = chatTopic;
        this.userRepository = userRepository;
    }


    // 최신 메시지 조회
    public List<ChatMessageResponse> getRecent(String roomId, int size){
        return chatMessageRepository
                .findByRoomIdOrderByCreatedAtDesc(roomId, PageRequest.of(0, size))
                .stream()
                .map(this::chatMessageResponse)
                .toList();
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
                        .map(f -> ChatFile.builder()
                                .fileName(f.getFileName())
                                .fileUrl(f.getFileUrl())
                                .build())
                        .toList())
                .build();

        chatMessage = chatMessageRepository.save(chatMessage);

        // 채팅방 최근 미리보기/시간 갱신
        room.setRecentText(previewText(chatMessageRequest));
        room.setRecentTime(Instant.now());
        chatRoomRepository.save(room);

        // 상대방 읽음 / 알림 처리
        for (String uid : room.getParticipantIds()){
            if (uid.equals(chatMessageRequest.getSenderId())) continue;
            String presentRoom = chatStateService.currentRoomOf(uid);
            if (presentRoom == null || !presentRoom.equals(room.getId())) {
                chatStateService.incUnread(room.getId(), uid);
                chatStateService.incAlarm(uid);
            }
        }

        ChatMessageResponse payload = chatMessageResponse(chatMessage);
        // 단일 인스턴스용 STOMP 전송
        messaging.convertAndSend("/topic/chat/room/" + chatMessageRequest.getRoomId(), payload);
        // 멀티 인스턴스용 Redis Pub/Sub 전파
        redisTemplate.convertAndSend(chatTopic.getTopic(), payload);
    }

    private ChatMessageResponse chatMessageResponse(ChatMessage m){

        List<ChatFileRequest> chatFileRequests = m.getFiles().stream().map(file -> {
            ChatFileRequest request = new ChatFileRequest();
            request.setFileName(file.getFileName());
            request.setFileUrl(file.getFileUrl());
            return request;
        }).toList();

        User sender = null;
        try {
            sender = userRepository.findByEmail(m.getSenderId()).orElse(null);
        } catch (Exception e) {
        }
        String nickname = (sender != null) ? sender.getNickname() : null;
        String profileImageUrl = (sender != null) ? sender.getProfileImageUrl() : null;

        return ChatMessageResponse.builder()
                .id(m.getId())
                .roomId(m.getRoomId())
                .senderId(m.getSenderId())
                .senderNickname(nickname)
                .senderProfileImageUrl(profileImageUrl)
                .messageType(m.getMessageType())
                .message(m.getMessage())
                .files(chatFileRequests)
                .createdAt(m.getCreatedAt())
                .build();
    }

    // 최근 미리보기 셋팅(rule)
    private String previewText(ChatMessageRequest chatMessageRequest){
        return switch (chatMessageRequest.getMessageType()){
            case FILE -> "파일을 보냈습니다.";
            case IMAGE -> "이미지를 보냈습니다.";
            case SYSTEM -> chatMessageRequest.getMessage() == null ? "시스템 메시지" : chatMessageRequest.getMessage();
            default -> chatMessageRequest.getMessage();
        };
    }
}
