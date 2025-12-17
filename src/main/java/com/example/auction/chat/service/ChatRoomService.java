package com.example.auction.chat.service;

import com.example.auction.chat.domain.ChatRoom;
import com.example.auction.chat.dto.ChatMemberResponse;
import com.example.auction.chat.dto.ChatRoomOpenRequest;
import com.example.auction.chat.dto.ChatRoomResponse;
import com.example.auction.chat.dto.ChatUserSummary;
import com.example.auction.chat.repository.ChatRoomRepository;
import com.example.auction.common.domain.DelYN;
import com.example.auction.notification.service.InquiryNotificationService;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatStateService chatStateService;
    private final UserRepository userRepository;
    private final InquiryResolver inquiryResolver;
    private final InquiryNotificationService inquiryNotificationService;
    private final ChatUserCacheService chatUserCacheService;

    public ChatRoomService(ChatRoomRepository chatRoomRepository, ChatStateService chatStateService, UserRepository userRepository, InquiryResolver inquiryResolver, InquiryNotificationService inquiryNotificationService, ChatUserCacheService chatUserCacheService) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatStateService = chatStateService;
        this.userRepository = userRepository;
        this.inquiryResolver = inquiryResolver;
        this.inquiryNotificationService = inquiryNotificationService;
        this.chatUserCacheService = chatUserCacheService;
    }

    // 방생성관련 user1 의 userId user2의 userId
    private String keyOf(String a, String b) {
        return (a.compareTo(b) <= 0) ? a + "_" + b : b + "_" + a;
    }

    private ChatUserSummary getCurrentUserSummary() {
        return chatUserCacheService.getCurrentUser();
    }

    public ChatRoom openRoom(ChatRoomOpenRequest request){
        ChatUserSummary me = getCurrentUserSummary();

        String targetEmail = request.getTargetId();

        User target = userRepository.findByEmailAndDelYn(targetEmail, DelYN.N)
                .orElseThrow(() -> new IllegalArgumentException("상대 유저를 찾을 수 없습니다."));

        if (request.isAdminChat()) {
            if (me.getAuthority() != Authority.ADMIN && me.getAuthority() != Authority.INQUIRY) {
                throw new IllegalStateException("ADMIN 또는 INQUIRY만 운영 채팅을 생성할 수 있습니다.");
            }
        }

        String key = keyOf(me.getEmail(), target.getEmail());

        return chatRoomRepository.findByRoomKey(key).orElseGet(() -> {
            ChatRoom chatRoom = request.toEntityForNormal(
                    key,
                    me.getEmail(),
                    target.getEmail(),
                    Instant.now()
            );
            return chatRoomRepository.save(chatRoom);
        });
    }

    @Transactional
    public ChatRoom openInquiryRoom(ChatRoomOpenRequest request) {
        ChatUserSummary me = getCurrentUserSummary();
        User inquirer = inquiryResolver.resolve();

        String userEmail = me.getEmail();
        String inquirerEmail = inquirer.getEmail();

        if (userEmail.equals(inquirerEmail)) {
            throw new IllegalStateException("담당자 본인은 문의방을 열 수 없습니다.");
        }

        String key = keyOf(userEmail, inquirerEmail);

        return chatRoomRepository.findByRoomKey(key).orElseGet(() -> {
            ChatRoom chatRoom = request.toEntityForInquiry(
                    key,
                    userEmail,
                    inquirerEmail,
                    Instant.now()
            );
            ChatRoom saved = chatRoomRepository.save(chatRoom);

//            User meEntity = userRepository.findByEmailAndDelYn(userEmail, DelYN.N)
//                    .orElseThrow(() -> new IllegalStateException("현재 유저를 찾을 수 없습니다."));


            // 새 문의방 생성 → 담당자에게 푸시
            inquiryNotificationService.notifyNewInquiryRoom(saved, me, inquirer);

            return saved;
        });
    }


    // 내 채팅방 목록
    public List<ChatRoomResponse> listMyRooms() {
        ChatUserSummary me = getCurrentUserSummary();
        String myEmail = me.getEmail();

        List<ChatRoom> rooms = chatRoomRepository.findByParticipantIdsContains(myEmail);
        rooms.sort(Comparator.comparing(
                ChatRoom::getRecentTime,
                Comparator.nullsLast(Comparator.naturalOrder())
        ).reversed());

        Set<String> allEmails = rooms.stream()
                .flatMap(room -> room.getParticipantIds().stream())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, ChatUserSummary> userMap = chatUserCacheService.getByEmails(allEmails);

        List<ChatRoomResponse> result = new ArrayList<>();
        for (ChatRoom chatRoom : rooms) {
            int unread = chatStateService.getUnread(chatRoom.getId(), myEmail);
            String roomName = buildRoomName(me, chatRoom, userMap);
            result.add(ChatRoomResponse.fromEntity(chatRoom, unread, roomName));
        }
        return result;


    }

    public void enter(String roomId) {
        ChatUserSummary me = getCurrentUserSummary();
        String email = me.getEmail();
        chatStateService.enterRoom(email, roomId);

        int alarm = Math.max(0, chatStateService.getAlarm(email) - chatStateService.getUnread(roomId, email));
        chatStateService.setAlarm(email, alarm);
        chatStateService.clearUnread(roomId, email);
    }

    public void exit() {
        ChatUserSummary me = getCurrentUserSummary();
        chatStateService.exitRoom(me.getEmail());
    }


    @Transactional
    public void inviteInquiry(String roomId, String targetInquiryEmail) {
        ChatUserSummary inviter = getCurrentUserSummary();
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

        if (!room.getParticipantIds().contains(inviter.getEmail())) {
            throw new IllegalStateException("해당 채팅방 참가자만 초대할 수 있습니다.");
        }

        if (inviter.getAuthority() != Authority.INQUIRY && inviter.getAuthority() != Authority.ADMIN) {
            throw new IllegalStateException("INQUIRY 또는 ADMIN 권한만 다른 INQUIRY를 초대할 수 있습니다.");
        }

        User target = userRepository.findByEmailAndDelYn(targetInquiryEmail, DelYN.N)
                .orElseThrow(() -> new IllegalArgumentException("대상 문의 담당자를 찾을 수 없습니다."));

        if (target.getAuthority() != Authority.INQUIRY) {
            throw new IllegalStateException("INQUIRY 권한 사용자만 초대할 수 있습니다.");
        }

        if (room.getParticipantIds().contains(targetInquiryEmail)) {
            return;
        }

        List<String> updated = new ArrayList<>(room.getParticipantIds());
        updated.add(targetInquiryEmail);
        room.setParticipantIds(updated);
        chatRoomRepository.save(room);
    }


    @Transactional(readOnly = true)
    public List<ChatMemberResponse> listMembersByAuthority(Authority authority) {
        return userRepository.findAllByAuthorityAndDelYn(authority, DelYN.N)
                .stream()
                .map(ChatMemberResponse::fromEntity)
                .toList();
    }

    // 방 이름 생성 로직 관련
    private String buildRoomName(ChatUserSummary me,
                                 ChatRoom room,
                                 Map<String, ChatUserSummary> userMap) {

        List<ChatUserSummary> participants = room.getParticipantIds().stream()
                .map(userMap::get)
                .filter(Objects::nonNull)
                .toList();

        boolean hasUser = participants.stream().anyMatch(u -> u.getAuthority() == Authority.USER);
        boolean hasAdmin = participants.stream().anyMatch(u -> u.getAuthority() == Authority.ADMIN);
        boolean hasInquiry = participants.stream().anyMatch(u -> u.getAuthority() == Authority.INQUIRY);

        if (room.isAdminChat() && hasUser) {
            if (me.getAuthority() == Authority.USER) {
                return "문의하기";
            }
            ChatUserSummary customer = participants.stream()
                    .filter(u -> u.getAuthority() == Authority.USER)
                    .findFirst()
                    .orElse(null);
            if (customer != null && customer.getNickname() != null) {
                return customer.getNickname();
            }
            return "문의하기";
        }

        if (room.isAdminChat() && !hasUser && hasAdmin && hasInquiry) {
            ChatUserSummary admin = participants.stream()
                    .filter(u -> u.getAuthority() == Authority.ADMIN)
                    .findFirst()
                    .orElse(null);
            ChatUserSummary inquiry = participants.stream()
                    .filter(u -> u.getAuthority() == Authority.INQUIRY)
                    .findFirst()
                    .orElse(null);

            if (admin != null && inquiry != null) {
                String adminName = admin.getNickname() != null ? admin.getNickname() : admin.getEmail();
                String inquiryName = inquiry.getNickname() != null ? inquiry.getNickname() : inquiry.getEmail();
                return adminName + " - " + inquiryName;
            }
        }

        List<ChatUserSummary> others = participants.stream()
                .filter(u -> !u.getEmail().equals(me.getEmail()))
                .toList();

        if (others.isEmpty()) {
            return "나와의 채팅";
        }

        if (others.size() == 1) {
            ChatUserSummary other = others.get(0);
            return other.getNickname() != null ? other.getNickname() : other.getEmail();
        }

        return others.stream()
                .map(u -> u.getNickname() != null ? u.getNickname() : u.getEmail())
                .reduce((a, b) -> a + ", " + b)
                .orElse("그룹 채팅");


    }
}