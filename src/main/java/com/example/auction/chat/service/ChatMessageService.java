package com.example.auction.chat.service;

import com.example.auction.chat.domain.ChatMessage;
import com.example.auction.chat.domain.ChatRoom;
import com.example.auction.chat.domain.MessageType;
import com.example.auction.chat.dto.ChatMessageRequest;
import com.example.auction.chat.dto.ChatMessageResponse;
import com.example.auction.chat.dto.ChatUserSummary;
import com.example.auction.chat.repository.ChatMessageRepository;
import com.example.auction.chat.repository.ChatRoomRepository;
import com.example.auction.common.domain.DelYN;
import com.example.auction.notification.service.InquiryNotificationService;
import com.example.auction.push.service.PushService;
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
import java.util.*;
import java.util.stream.Collectors;

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
    private final ChatUserCacheService chatUserCacheService;

    public ChatMessageService(ChatMessageRepository chatMessageRepository, ChatRoomRepository chatRoomRepository, ChatStateService chatStateService, SimpMessageSendingOperations messaging, @Qualifier("chatRoom") RedisTemplate<String, Object> redisTemplate, @Qualifier("chat") ChannelTopic chatTopic, UserRepository userRepository, InquiryNotificationService inquiryNotificationService, PushService pushService, ChatUserCacheService chatUserCacheService) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.chatStateService = chatStateService;
        this.messaging = messaging;
        this.redisTemplate = redisTemplate;
        this.chatTopic = chatTopic;
        this.userRepository = userRepository;
        this.inquiryNotificationService = inquiryNotificationService;
        this.pushService = pushService;
        this.chatUserCacheService = chatUserCacheService;
    }

    private static final int MAX_TALK_LEN = 1000;
    private static final int MAX_URL_LEN = 2000;
    private static final int MAX_PREVIEW_LEN = 120;

    private void validateMessage(ChatMessageRequest req) {
        MessageType type = req.getMessageType() == null ? MessageType.TALK : req.getMessageType();
        String msg = req.getMessage();

        if (type == MessageType.TALK) {
            if (msg == null || msg.trim().isEmpty()) {
                throw new IllegalArgumentException("메시지는 비어 있을 수 없습니다.");
            }
            if (msg.length() > MAX_TALK_LEN) {
                throw new IllegalArgumentException("메시지는 최대 " + MAX_TALK_LEN + "자까지 입력할 수 있습니다.");
            }
            return;
        }
        if (msg != null) {
            int limit = (type == MessageType.IMAGE || type == MessageType.FILE) ? MAX_URL_LEN : MAX_TALK_LEN;
            if (msg.length() > limit) {
                throw new IllegalArgumentException("메시지는 최대 " + limit + "자까지 입력할 수 있습니다.");
            }
        }
    }
    private String clip(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return (t.length() <= max) ? t : t.substring(0, max) + "…";
    }

    // 최신 메시지 조회
    public List<ChatMessageResponse> getRecent(String roomId, int size) {
        //  메시지 목록 조회 (기존과 동일)
        List<ChatMessage> messages = chatMessageRepository
                .findByRoomIdOrderByCreatedAtDesc(roomId, PageRequest.of(0, size));

        // 발신자 이메일 목록 추출
        List<String> senderEmails = messages.stream()
                .map(ChatMessage::getSenderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();


        Map<String, ChatUserSummary> senderMap = chatUserCacheService.getByEmails(senderEmails);

        return messages.stream()
                .map(m -> ChatMessageResponse
                        .fromEntity(m, senderMap.get(m.getSenderId())))
                .toList();
    }

    public void send(ChatMessageRequest chatMessageRequest) {
        validateMessage(chatMessageRequest);
        ChatUserSummary senderSummary = chatUserCacheService.getCurrentUser();

        String senderEmail = senderSummary.getEmail();

        // 방 조회
        ChatRoom room = chatRoomRepository.findById(chatMessageRequest.getRoomId())
                .orElseThrow(() -> new IllegalArgumentException("방을 찾을 수 없습니다."));

        // 방 참가자인지 검증
        if (!room.getParticipantIds().contains(senderEmail)) {
            throw new IllegalStateException("해당 채팅방 참가자만 메시지를 보낼 수 있습니다.");
        }

        // 메시지 엔티티 생성 및 저장
        ChatMessage chatMessage = chatMessageRequest.toEntity(senderEmail);
        chatMessage = chatMessageRepository.save(chatMessage);

        // 최근 메시지 미리보기 + 시간 업데이트
        String preview = clip(previewText(chatMessageRequest), MAX_PREVIEW_LEN);
        room.updateRecent(preview, Instant.now());
        chatRoomRepository.save(room);

        // 문의방이면 담당자/고객 알림 (기존 로직 유지)
        try {
            inquiryNotificationService.notifyOnNewMessage(room, senderSummary, preview);
        } catch (Exception e) {
            log.warn("[ChatNotify] 문의 메시지 알림 처리 중 예외 roomId={}", room.getId(), e);
        }

        // 상대방 읽음/알림 처리 (기존 로직 그대로)
        for (String uid : room.getParticipantIds()) {
            if (uid.equals(senderEmail)) continue;

            String presentRoom = chatStateService.currentRoomOf(uid);
            if (presentRoom == null || !presentRoom.equals(room.getId())) {
                chatStateService.incUnread(room.getId(), uid);
                chatStateService.incAlarm(uid);
            }
        }
        // sendInquiryPushIfNeeded(room, chatMessage, preview);

        // 최종 payload 생성
        ChatMessageResponse payload = ChatMessageResponse.fromEntity(chatMessage, senderSummary);

        // 단일 인스턴스용 STOMP 전송
        messaging.convertAndSend("/topic/chat/room/" + chatMessageRequest.getRoomId(), payload);
        // 멀티 인스턴스용 Redis Pub/Sub 전파
        redisTemplate.convertAndSend(chatTopic.getTopic(), payload);
    }

    private ChatMessageResponse chatMessageResponse(ChatMessage chatMessage) {
        ChatUserSummary sender = null;
        try {
            sender = chatUserCacheService.getByEmail(chatMessage.getSenderId());
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
