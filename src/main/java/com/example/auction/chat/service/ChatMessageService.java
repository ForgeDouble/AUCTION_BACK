package com.example.auction.chat.service;

import com.example.auction.chat.domain.ChatMessage;
import com.example.auction.chat.domain.ChatRoom;
import com.example.auction.chat.domain.MessageType;
import com.example.auction.chat.dto.ChatMessageRequest;
import com.example.auction.chat.dto.ChatMessageResponse;
import com.example.auction.chat.repository.ChatMessageRepository;
import com.example.auction.chat.repository.ChatRoomRepository;
import com.example.auction.common.domain.DelYN;
import com.example.auction.notification.service.InquiryNotificationService;
import com.example.auction.push.service.PushService;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
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
    private final InquiryNotificationService inquiryNotificationService;
    private final PushService pushService;

    public ChatMessageService(ChatMessageRepository chatMessageRepository, ChatRoomRepository chatRoomRepository, ChatStateService chatStateService, SimpMessageSendingOperations messaging, @Qualifier("chatRoom") RedisTemplate<String, Object> redisTemplate, @Qualifier("chat") ChannelTopic chatTopic, UserRepository userRepository, InquiryNotificationService inquiryNotificationService, PushService pushService) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.chatStateService = chatStateService;
        this.messaging = messaging;
        this.redisTemplate = redisTemplate;
        this.chatTopic = chatTopic;
        this.userRepository = userRepository;
        this.inquiryNotificationService = inquiryNotificationService;
        this.pushService = pushService;
    }


    // 최신 메시지 조회
    public List<ChatMessageResponse> getRecent(String roomId, int size){
        return chatMessageRepository
                .findByRoomIdOrderByCreatedAtDesc(roomId, PageRequest.of(0, size))
                .stream()
                .map(this::chatMessageResponse)
                .toList();
    }

    public void send(ChatMessageRequest chatMessageRequest) {
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

        try {
            User sender = userRepository.findByEmail(senderEmail).orElse(null);
            if (sender != null) {
                inquiryNotificationService.notifyOnNewMessage(room, sender, preview);
            }
        } catch (Exception e) {
            log.warn("[ChatNotify] 문의 메시지 알림 처리 중 예외 roomId={}", room.getId(), e);
        }

        // 상대방 읽음 / 알림 처리
        for (String uid : room.getParticipantIds()){
            if (uid.equals(senderEmail)) continue;
            String presentRoom = chatStateService.currentRoomOf(uid);
            if (presentRoom == null || !presentRoom.equals(room.getId())) {
                chatStateService.incUnread(room.getId(), uid);
                chatStateService.incAlarm(uid);
            }
        }
//        sendInquiryPushIfNeeded(room, chatMessage, preview);

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



//    private void sendInquiryPushIfNeeded(ChatRoom room, ChatMessage chatMessage, String preview) {
//        try {
//            // 운영 채팅방이 아니면 푸시 안 보냄
//            if (!room.isAdminChat()) {
//                return;
//            }
//
//            // 2) 발신자 정보
//            User sender = userRepository.findByEmailAndDelYn(chatMessage.getSenderId(), DelYN.N)
//                    .orElse(null);
//            if (sender == null) return;
//
//            List<User> participants = room.getParticipantIds().stream()
//                    .map(email -> userRepository.findByEmailAndDelYn(email, DelYN.N).orElse(null))
//                    .filter(Objects::nonNull)
//                    .toList();
//
//            if (participants.isEmpty()) return;
//
//            // 고객(USER) 1명
//            User customer = participants.stream()
//                    .filter(u -> u.getAuthority() == Authority.USER)
//                    .findFirst()
//                    .orElse(null);
//
//            // 문의 담당자/관리자(INQUIRY, ADMIN)
//            List<User> staffList = participants.stream()
//                    .filter(u -> u.getAuthority() == Authority.INQUIRY || u.getAuthority() == Authority.ADMIN)
//                    .toList();
//
//            if (customer == null || staffList.isEmpty()) {
//                // 문의 구조가 아닌 방
//                return;
//            }
//
//            String title;
//            String body;
//
//            // FCM data payload (Service Worker / FcmNotificationCenter 에서 사용)
//            Map<String, String> data = new java.util.HashMap<>();
//            data.put("type", "INQUIRY_NEW_MESSAGE");
//            data.put("roomId", room.getId());
//            data.put("senderEmail", sender.getEmail());
//            data.put("senderNickname", sender.getNickname() != null ? sender.getNickname() : "");
//            data.put("preview", preview != null ? preview : "");
//
//            if (sender.getAuthority() == Authority.USER) {
//                // 고객이 보낸 메시지 → 모든 문의 담당자에게 푸시
//                title = "새 문의 메시지 도착";
//                body = (sender.getNickname() != null ? sender.getNickname() : sender.getEmail()) + " : " + preview;
//
//                for (User staff : staffList) {
//                    try {
//                        pushService.sendToUser(staff.getUserId(), title, body, data);
//                    } catch (Exception e) {
//                        log.warn("[ChatPush] INQUIRY_NEW_MESSAGE to staff 실패 userId={}", staff.getUserId(), e);
//                    }
//                }
//            } else if (sender.getAuthority() == Authority.INQUIRY || sender.getAuthority() == Authority.ADMIN) {
//                // 문의 담당자/관리자 답변 → 고객에게 푸시
//                title = "문의 답변이 도착했습니다";
//                body = preview;
//
//                try {
//                    pushService.sendToUser(customer.getUserId(), title, body, data);
//                } catch (Exception e) {
//                    log.warn("[ChatPush] INQUIRY_NEW_MESSAGE to customer 실패 userId={}", customer.getUserId(), e);
//                }
//            }
//        } catch (Exception e) {
//            log.warn("[ChatPush] sendInquiryPushIfNeeded 처리 중 예외", e);
//        }
//
//    }
}
