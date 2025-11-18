package com.example.auction.chat.service;

import com.example.auction.chat.domain.ChatRoom;
import com.example.auction.chat.dto.ChatRoomOpenRequest;
import com.example.auction.chat.dto.ChatRoomResponse;
import com.example.auction.chat.repository.ChatRoomRepository;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatStateService chatStateService;
    private final UserRepository userRepository;
    private final InquiryResolver inquiryResolver;

    public ChatRoomService(ChatRoomRepository chatRoomRepository, ChatStateService chatStateService, UserRepository userRepository, InquiryResolver inquiryResolver) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatStateService = chatStateService;
        this.userRepository = userRepository;
        this.inquiryResolver = inquiryResolver;
    }

    // 방생성관련 user1 의 userId user2의 userId
    private String keyOf(String a, String b) {
        return (a.compareTo(b) <= 0) ? a + "_" + b : b + "_" + a;
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

    @Transactional
    public ChatRoom openInquiryRoom(ChatRoomOpenRequest request) {
        String meId = request.getUserId();
        if (meId == null || meId.isBlank()) {
            throw new IllegalArgumentException("userId가 필요합니다.");
        }

        User inquirer = inquiryResolver.resolve();

        String inquirerId = inquirer.getEmail();

        if (meId.equals(inquirerId)) {
            throw new IllegalStateException("담당자 본인은 문의방을 열 수 없습니다.");
        }

        String key = keyOf(meId, inquirerId);

        return chatRoomRepository.findByRoomKey(key).orElseGet(() -> {
            ChatRoom chatRoom = ChatRoom.builder()
                    .roomKey(key)
                    .participantIds(List.of(meId, inquirerId))  // 둘 다 email
                    .adminChat(true)
                    .recentTime(Instant.now())
                    .recentText("문의방이 생성되었습니다.")
                    .build();
            return chatRoomRepository.save(chatRoom);
        });
    }


    // 내 채팅방 목록
    public List<ChatRoomResponse> listMyRooms(String userId) {
        List<ChatRoom> rooms = chatRoomRepository.findByParticipantIdsContains(userId);
        rooms.sort(Comparator.comparing(ChatRoom::getRecentTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed());


        List<ChatRoomResponse> result = new ArrayList<>();
        for (ChatRoom chatRoom : rooms) {
            int unread = chatStateService.getUnread(chatRoom.getId(), userId);
            result.add(ChatRoomResponse.builder()
                    .roomId(chatRoom.getId())
                    .participantIds(chatRoom.getParticipantIds())
                    .recentText(chatRoom.getRecentText())
                    .recentTime(chatRoom.getRecentTime())
                    .unread(unread)
                    .adminChat(chatRoom.isAdminChat())
                    .build());
        }
        return result;
    }

    public void enter(String userId, String roomId) {
        chatStateService.enterRoom(userId, roomId);
        int alarm = Math.max(0, chatStateService.getAlarm(userId) - chatStateService.getUnread(roomId, userId));
        chatStateService.setAlarm(userId, alarm);
        chatStateService.clearUnread(roomId, userId);
    }

    public void exit(String userId) {
        chatStateService.exitRoom(userId);
    }
}