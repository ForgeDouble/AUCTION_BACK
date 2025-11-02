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
    private final ChatStateService chatStateService;

    public ChatRoomService(ChatRoomRepository chatRoomRepository, ChatStateService chatStateService) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatStateService = chatStateService;
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

    public List<ChatRoomResponse> listMyRooms(String userId){
        List<ChatRoom> rooms = chatRoomRepository.findByParticipantIdsContains(userId);
        rooms.sort(Comparator.comparing(ChatRoom::getRecentTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        List<ChatRoomResponse> chatRoomResponseList = new ArrayList<>();
        for (ChatRoom r : rooms){
            int unread = chatStateService.getUnread(r.getId(), userId);
            chatRoomResponseList.add(ChatRoomResponse.builder()
                    .roomId(r.getId())
                    .participantIds(r.getParticipantIds())
                    .recentText(r.getRecentText())
                    .recentTime(r.getRecentTime())
                    .unread(unread)
                    .build());
        }
        return chatRoomResponseList;
    }

    public void enter(String userId, String roomId){
        chatStateService.enterRoom(userId, roomId);
        int alarm = Math.max(0, chatStateService.getAlarm(userId) - chatStateService.getUnread(roomId, userId));
        chatStateService.setAlarm(userId, alarm);
        chatStateService.clearUnread(roomId, userId);
    }

    public void exit(String myId){
        chatStateService.exitRoom(myId);
    }


}