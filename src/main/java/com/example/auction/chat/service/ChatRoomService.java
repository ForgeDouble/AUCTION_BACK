package com.example.auction.chat.service;

import com.example.auction.chat.domain.ChatRoom;
import com.example.auction.chat.domain.ChatRoomType;
import com.example.auction.chat.dto.*;
import com.example.auction.chat.repository.ChatRoomRepository;
import com.example.auction.common.domain.DelYN;
import com.example.auction.notification.service.InquiryNotificationService;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatRoomService {
    private static final String ADMIN_LOUNGE_KEY = "ADMIN_LOUNGE";

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

    // 관리자 인사담당자 확인 함수
    private void requireStaff(ChatUserSummary me) {
        if (me.getAuthority() != Authority.ADMIN && me.getAuthority() != Authority.INQUIRY) {
            throw new IllegalStateException("ADMIN 또는 INQUIRY만 접근 가능합니다.");
        }
    }

    private ChatRoom loadRoomOrThrow(String roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
    }

    private void requireParticipant(ChatRoom room, String email) {
        if (!room.getParticipantIds().contains(email)) {
            throw new IllegalStateException("해당 채팅방 참가자만 접근할 수 있습니다.");
        }
    }

    @Transactional
    public ChatRoom openRoom(ChatRoomOpenRequest request) {
        ChatUserSummary me = getCurrentUserSummary();

        String targetEmail = request.getTargetId();
        User target = userRepository.findByEmailAndDelYn(targetEmail, DelYN.N)
                .orElseThrow(() -> new IllegalArgumentException("상대 유저를 찾을 수 없습니다."));

        if (request.isAdminChat()) {
            requireStaff(me);
        }

        String key = keyOf(me.getEmail(), target.getEmail());

        ChatRoom room = chatRoomRepository.findByRoomKey(key).orElse(null);
        if (room == null) {
            ChatRoom created = request.toEntityForNormal(
                    key,
                    me.getEmail(),
                    target.getEmail(),
                    Instant.now()
            );
            created.setRoomType(ChatRoomType.NORMAL);
            created.setAdminChat(request.isAdminChat());
            return chatRoomRepository.save(created);
        }

        boolean changed = false;
        List<String> p = new ArrayList<>(room.getParticipantIds());
        if (!p.contains(me.getEmail())) { p.add(me.getEmail()); changed = true; }
        if (!p.contains(target.getEmail())) { p.add(target.getEmail()); changed = true; }
        if (changed) {
            room.setParticipantIds(p);
            room = chatRoomRepository.save(room);
        }
        return room;
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

        ChatRoom existing = chatRoomRepository.findByRoomKey(key).orElse(null);
        if (existing != null) {
            boolean changed = false;
            List<String> p = new ArrayList<>(existing.getParticipantIds());
            if (!p.contains(userEmail)) { p.add(userEmail); changed = true; }
            if (!p.contains(inquirerEmail)) { p.add(inquirerEmail); changed = true; }
            if (changed) {
                existing.setParticipantIds(p);
                existing = chatRoomRepository.save(existing);
            }
            return existing;
        }

        ChatRoom chatRoom = request.toEntityForInquiry(
                key,
                userEmail,
                inquirerEmail,
                Instant.now()
        );
        chatRoom.setRoomType(ChatRoomType.INQUIRY);
        chatRoom.setAdminChat(true);
        if (!StringUtils.hasText(chatRoom.getTitle())) chatRoom.setTitle("문의하기");

        ChatRoom saved = chatRoomRepository.save(chatRoom);

        inquiryNotificationService.notifyNewInquiryRoom(saved, me, inquirer);
        return saved;
    }

    // 운영자 라운지 관련 채팅방 ( 계정 생성 시 자동 참가 목적 )
    @Transactional
    public ChatRoom openAdminLounge() {
        ChatUserSummary me = getCurrentUserSummary();
        requireStaff(me);

        ChatRoom room = chatRoomRepository.findByRoomKey(ADMIN_LOUNGE_KEY).orElse(null);
        if (room == null) {
            room = ChatRoom.builder()
                    .roomKey(ADMIN_LOUNGE_KEY)
                    .roomType(ChatRoomType.ADMIN_GROUP)
                    .adminChat(true)
                    .title("운영자 단체방")
                    .participantIds(new ArrayList<>())
                    .createdAt(Instant.now())
                    .build();
        }

        List<String> p = new ArrayList<>(room.getParticipantIds());
        if (!p.contains(me.getEmail())) p.add(me.getEmail());
        room.setParticipantIds(p);

        return chatRoomRepository.save(room);
    }

    // 운영진 그룹방 생성
    @Transactional
    public ChatRoom createStaffGroup(ChatRoomCreateGroupRequest chatRoomCreateGroupRequest) {
        ChatUserSummary me = getCurrentUserSummary();
        requireStaff(me);

        String key = "STAFF_GROUP:" + UUID.randomUUID();

        List<String> participants = new ArrayList<>();
        participants.add(me.getEmail());

        if (chatRoomCreateGroupRequest.getParticipantEmails() != null) {
            for (String email : chatRoomCreateGroupRequest.getParticipantEmails()) {
                if (email == null || email.isBlank()) continue;

                User u = userRepository.findByEmailAndDelYn(email.trim(), DelYN.N)
                        .orElseThrow(() -> new IllegalArgumentException("초대 대상 유저를 찾을 수 없습니다: " + email));

                if (u.getAuthority() != Authority.ADMIN && u.getAuthority() != Authority.INQUIRY) {
                    throw new IllegalStateException("운영진 그룹방에는 ADMIN/INQUIRY만 초대할 수 있습니다.");
                }
                if (!participants.contains(u.getEmail())) participants.add(u.getEmail());
            }
        }

        ChatRoom room = ChatRoom.builder()
                .roomKey(key)
                .roomType(ChatRoomType.STAFF_GROUP)
                .adminChat(true)
                .title(StringUtils.hasText(chatRoomCreateGroupRequest.getTitle()) ? chatRoomCreateGroupRequest.getTitle().trim() : "운영진 그룹채팅")
                .participantIds(participants)
                .createdAt(Instant.now())
                .build();

        return chatRoomRepository.save(room);
    }

    // 운영자 채팅방 초대
    @Transactional
    public void inviteStaffMember(String roomId, String targetEmail) {
        ChatUserSummary me = getCurrentUserSummary();
        requireStaff(me);

        ChatRoom room = loadRoomOrThrow(roomId);
        requireParticipant(room, me.getEmail());

        if (!(room.getRoomType() == ChatRoomType.ADMIN_GROUP || room.getRoomType() == ChatRoomType.STAFF_GROUP || room.getRoomType() == ChatRoomType.INQUIRY)) {
            throw new IllegalStateException("운영진 초대는 운영 채팅방에서만 가능합니다.");
        }

        User target = userRepository.findByEmailAndDelYn(targetEmail, DelYN.N)
                .orElseThrow(() -> new IllegalArgumentException("대상 유저를 찾을 수 없습니다."));

        if (target.getAuthority() != Authority.ADMIN && target.getAuthority() != Authority.INQUIRY) {
            throw new IllegalStateException("ADMIN/INQUIRY만 초대할 수 있습니다.");
        }

        List<String> updated = new ArrayList<>(room.getParticipantIds());
        if (!updated.contains(target.getEmail())) {
            updated.add(target.getEmail());
            room.setParticipantIds(updated);
            chatRoomRepository.save(room);
        }
    }

    // 방 나가기
    @Transactional
    public void leaveRoom(String roomId) {
        ChatUserSummary me = getCurrentUserSummary();

        ChatRoom room = loadRoomOrThrow(roomId);
        if (ADMIN_LOUNGE_KEY.equals(room.getRoomKey())) {
            throw new IllegalStateException("운영자 단체방은 나갈 수 없습니다.");
        }
        String email = me.getEmail();
        requireParticipant(room, email);

        String cur = chatStateService.currentRoomOf(email);
        if (cur != null && cur.equals(roomId)) {
            chatStateService.exitRoom(email);
        }

        chatStateService.clearUnread(roomId, email);

        List<String> updated = new ArrayList<>(room.getParticipantIds());
        updated.removeIf(x -> x != null && x.equals(email));
        room.setParticipantIds(updated);

        if (updated.isEmpty()) {
            chatRoomRepository.delete(room);
            return;
        }
        chatRoomRepository.save(room);
    }

    // 내 채팅방 목록 -> 지금 기준이 title 우선
    @Transactional(readOnly = true)
    public List<ChatRoomResponse> listMyRooms() {
        ChatUserSummary me = getCurrentUserSummary();
        String myEmail = me.getEmail();

        List<ChatRoom> rooms = chatRoomRepository.findByParticipantIdsContains(myEmail);
        rooms.sort(Comparator.comparing(
                ChatRoom::getRecentTime,
                Comparator.nullsLast(Comparator.naturalOrder())
        ).reversed());

        Set<String> allEmails = rooms.stream()
                .flatMap(r -> r.getParticipantIds().stream())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, ChatUserSummary> userMap = chatUserCacheService.getByEmails(allEmails);

        List<ChatRoomResponse> result = new ArrayList<>();
        for (ChatRoom room : rooms) {
            int unread = chatStateService.getUnread(room.getId(), myEmail);

            String roomName;
            if (StringUtils.hasText(room.getTitle())) {
                roomName = room.getTitle();
            } else {
                roomName = buildRoomName(me, room, userMap);
            }
            result.add(ChatRoomResponse.fromEntity(room, unread, roomName));
        }
        return result;
    }

    public void enter(String roomId) {
        ChatUserSummary me = getCurrentUserSummary();
        String email = me.getEmail();

        ChatRoom room = loadRoomOrThrow(roomId);
        requireParticipant(room, email);

        chatStateService.enterRoom(email, roomId);

        int alarm = Math.max(0, chatStateService.getAlarm(email) - chatStateService.getUnread(roomId, email));
        chatStateService.setAlarm(email, alarm);
        chatStateService.clearUnread(roomId, email);
    }

    public void exit() {
        ChatUserSummary me = getCurrentUserSummary();
        chatStateService.exitRoom(me.getEmail());
    }


    @Transactional(readOnly = true)
    public List<ChatMemberResponse> listRoomMembers(String roomId) {
        ChatUserSummary me = getCurrentUserSummary();
        ChatRoom room = loadRoomOrThrow(roomId);
        requireParticipant(room, me.getEmail());

        Map<String, ChatUserSummary> map = chatUserCacheService.getByEmails(room.getParticipantIds());
        return room.getParticipantIds().stream()
                .map(map::get)
                .filter(Objects::nonNull)
                .map(x -> ChatMemberResponse.builder()
                        .userId(x.getUserId())
                        .email(x.getEmail())
                        .nickname(x.getNickname())
                        .authority(x.getAuthority())
                        .profileImageUrl(x.getProfileImageUrl())
                        .build())
                .toList();
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
    private String buildRoomName(ChatUserSummary me, ChatRoom room, Map<String, ChatUserSummary> userMap) {
        List<ChatUserSummary> participants = room.getParticipantIds().stream()
                .map(userMap::get)
                .filter(Objects::nonNull)
                .toList();

        boolean hasUser = participants.stream().anyMatch(u -> u.getAuthority() == Authority.USER);
        boolean hasAdmin = participants.stream().anyMatch(u -> u.getAuthority() == Authority.ADMIN);
        boolean hasInquiry = participants.stream().anyMatch(u -> u.getAuthority() == Authority.INQUIRY);

        if (room.isAdminChat() && hasUser) {
            if (me.getAuthority() == Authority.USER) return "문의하기";

            ChatUserSummary customer = participants.stream()
                    .filter(u -> u.getAuthority() == Authority.USER)
                    .findFirst().orElse(null);

            if (customer != null && customer.getNickname() != null) return customer.getNickname();
            return "문의하기";
        }

        if (room.isAdminChat() && !hasUser && hasAdmin && hasInquiry) {
            ChatUserSummary admin = participants.stream()
                    .filter(u -> u.getAuthority() == Authority.ADMIN)
                    .findFirst().orElse(null);
            ChatUserSummary inquiry = participants.stream()
                    .filter(u -> u.getAuthority() == Authority.INQUIRY)
                    .findFirst().orElse(null);

            if (admin != null && inquiry != null) {
                String adminName = admin.getNickname() != null ? admin.getNickname() : admin.getEmail();
                String inquiryName = inquiry.getNickname() != null ? inquiry.getNickname() : inquiry.getEmail();
                return adminName + " - " + inquiryName;
            }
        }

        List<ChatUserSummary> others = participants.stream()
                .filter(u -> !u.getEmail().equals(me.getEmail()))
                .toList();

        if (others.isEmpty()) return "나와의 채팅";

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