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
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.core.context.SecurityContextHolder;
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
        // securitycontextholder 을 사용해도 되나 이거? 일단 sender email 추출을 위한 코드 적용
        String senderEmail = SecurityContextHolder.getContext().getAuthentication().getName();

        ChatRoom room = chatRoomRepository.findById(chatMessageRequest.getRoomId())
                .orElseThrow(() -> new IllegalArgumentException("방을 찾을 수 없습니다"));

        if (!room.getParticipantIds().contains(senderEmail)) {
            throw new IllegalStateException("해당 채팅방 참가자만 메시지를 보낼 수 있습니다.");
        }

        ChatMessage chatMessage = chatMessageRequest.toEntity(senderEmail);
        chatMessage = chatMessageRepository.save(chatMessage);

        String preview = previewText(chatMessageRequest);
        room.updateRecent(preview, Instant.now());
        chatRoomRepository.save(room);

        // 상대방 읽음 / 알림 처리
        for (String uid : room.getParticipantIds()){
            if (uid.equals(senderEmail)) continue;
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

    private ChatMessageResponse chatMessageResponse(ChatMessage chatMessage){

        User sender = null;
        try {
            sender = userRepository.findByEmail(chatMessage.getSenderId()).orElse(null);
        } catch (Exception ignored) {}

        return ChatMessageResponse.fromEntity(chatMessage, sender);
    }

    // 최근 채팅 미리보기 셋팅
    private String previewText(ChatMessageRequest chatMessageRequest){
        MessageType type = chatMessageRequest.getMessageType();
        if (type == null) return chatMessageRequest.getMessage();

        return switch (type){
            case FILE -> "파일을 보냈습니다.";
            case IMAGE -> "이미지를 보냈습니다.";
            case SYSTEM -> chatMessageRequest.getMessage() == null ? "시스템 메시지" : chatMessageRequest.getMessage();
            default -> chatMessageRequest.getMessage();
        };
    }
}
