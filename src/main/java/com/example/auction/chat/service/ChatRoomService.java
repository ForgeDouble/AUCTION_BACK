package com.example.auction.chat.service;

import com.example.auction.chat.domain.ChatRoom;
import com.example.auction.chat.dto.ChatMemberResponse;
import com.example.auction.chat.dto.ChatRoomOpenRequest;
import com.example.auction.chat.dto.ChatRoomResponse;
import com.example.auction.chat.repository.ChatRoomRepository;
import com.example.auction.common.domain.DelYN;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

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

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new RuntimeException("현재 로그인한 유저를 찾을 수 없습니다."));
    }

    public ChatRoom openRoom(ChatRoomOpenRequest request){
        User me = getCurrentUser();
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
        User me = getCurrentUser();
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
            return chatRoomRepository.save(chatRoom);
        });
    }


    // 내 채팅방 목록
    @Transactional(readOnly = true)
    public List<ChatRoomResponse> listMyRooms() {
        User me = getCurrentUser();
        String myEmail = me.getEmail();

        List<ChatRoom> rooms = chatRoomRepository.findByParticipantIdsContains(myEmail);
        rooms.sort(Comparator.comparing(ChatRoom::getRecentTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        List<ChatRoomResponse> result = new ArrayList<>();
        for (ChatRoom chatRoom : rooms) {
            int unread = chatStateService.getUnread(chatRoom.getId(), myEmail);
            String roomName = buildRoomName(me, chatRoom);
            result.add(ChatRoomResponse.fromEntity(chatRoom, unread, roomName));
        }
        return result;
    }

    public void enter(String roomId) {
        User me = getCurrentUser();
        String email = me.getEmail();
        chatStateService.enterRoom(email, roomId);

        int alarm = Math.max(0, chatStateService.getAlarm(email) - chatStateService.getUnread(roomId, email));
        chatStateService.setAlarm(email, alarm);
        chatStateService.clearUnread(roomId, email);
    }

    public void exit() {
        User me = getCurrentUser();
        chatStateService.exitRoom(me.getEmail());
    }


    @Transactional
    public void inviteInquiry(String roomId, String targetInquiryEmail) {
        User inviter = getCurrentUser();
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
    private String buildRoomName(User me, ChatRoom room) {
        List<User> participants = room.getParticipantIds().stream()
                .map(email -> userRepository.findByEmail(email).orElse(null))
                .filter(Objects::nonNull)
                .toList();

        boolean hasUser = participants.stream().anyMatch(u -> u.getAuthority() == Authority.USER);
        boolean hasAdmin = participants.stream().anyMatch(u -> u.getAuthority() == Authority.ADMIN);
        boolean hasInquiry = participants.stream().anyMatch(u -> u.getAuthority() == Authority.INQUIRY);

        if (room.isAdminChat() && hasUser) {
            if (me.getAuthority() == Authority.USER) {
                return "문의하기";
            }
            User customer = participants.stream()
                    .filter(u -> u.getAuthority() == Authority.USER)
                    .findFirst()
                    .orElse(null);
            if (customer != null) {
                return customer.getNickname();
            }
            return "문의하기";
        }

        if (room.isAdminChat() && !hasUser && hasAdmin && hasInquiry) {
            User admin = participants.stream()
                    .filter(u -> u.getAuthority() == Authority.ADMIN)
                    .findFirst()
                    .orElse(null);
            User inquiry = participants.stream()
                    .filter(u -> u.getAuthority() == Authority.INQUIRY)
                    .findFirst()
                    .orElse(null);

            if (admin != null && inquiry != null) {
                return admin.getNickname() + " - " + inquiry.getNickname();
            }
        }

        List<User> others = participants.stream()
                .filter(u -> !u.getEmail().equals(me.getEmail()))
                .toList();

        if (others.isEmpty()) {
            return "나와의 채팅";
        }

        if (others.size() == 1) {
            return others.get(0).getNickname();
        }

        return others.stream()
                .map(User::getNickname)
                .reduce((a, b) -> a + ", " + b)
                .orElse("그룹 채팅");
    }
}