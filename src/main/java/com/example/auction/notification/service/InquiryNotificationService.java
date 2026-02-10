package com.example.auction.notification.service;

import com.example.auction.chat.domain.ChatRoom;
import com.example.auction.chat.domain.ChatRoomType;
import com.example.auction.chat.dto.ChatUserSummary;
import com.example.auction.chat.service.ChatStateService;
import com.example.auction.chat.service.ChatUserCacheService;
import com.example.auction.common.domain.DelYN;
import com.example.auction.notification.domain.NotificationCategory;
import com.example.auction.push.service.PushService;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InquiryNotificationService {

    private final PushService pushService;
    private final ChatUserCacheService chatUserCacheService;
    private final ChatStateService chatStateService;


    // 새 문의방 생성 알림: 고객 → 담당자 1명
    public void notifyNewInquiryRoom(ChatRoom room, ChatUserSummary customer, User inquirer) {
        if (room == null || customer == null || inquirer == null) {
            log.warn("[InquiryNotify] 새 문의 알림 파라미터 누락 room/customer/inquirer null");
            return;
        }

        String title = "새 문의가 접수되었습니다";
        String body = customer.getNickname() + "님의 문의가 접수되었습니다.";

        Map<String, String> data = Map.of(
                "type", "INQUIRY_NEW_ROOM",
                "roomId", room.getId(),
                "customerUserId", String.valueOf(customer.getUserId()),
                "customerNickname", customer.getNickname()
        );

        try {
            pushService.sendToUser(inquirer.getUserId(), title, body, data, NotificationCategory.INQUIRY, true);
        } catch (Exception e) {
            log.warn("[InquiryNotify] 새 문의 알림 실패 roomId={}, inquirerId={}",
                    room.getId(), inquirer.getUserId(), e);
        }
    }

    //  문의방 내 새 메시지 알림 (유저 ↔ 담당자)
    public void notifyOnNewMessage(ChatRoom room, ChatUserSummary sender, String preview) {
        if (room == null || sender == null) return;

        // 운영/문의 채팅만
        if (!room.isAdminChat()) return;

        Map<String, ChatUserSummary> map = chatUserCacheService.getByEmails(room.getParticipantIds());
        List<ChatUserSummary> participants = map.values().stream()
                .filter(Objects::nonNull)
                .toList();

        if (participants.isEmpty()) return;

        String snippet = trimPreview(preview);

        boolean hasUser = participants.stream().anyMatch(u -> u.getAuthority() == Authority.USER);

        // 운영진 단체방 -> 운영진끼리 서로 알림
        boolean isStaffGroup =
                room.getRoomType() == ChatRoomType.ADMIN_GROUP
                        || room.getRoomType() == ChatRoomType.STAFF_GROUP
                        || (room.isAdminChat() && !hasUser);

        if (isStaffGroup) {
            String title = "새 운영 메시지";
            String body = sender.getNickname() + " : " + snippet;

            Map<String, String> data = Map.of(
                    "type", "STAFF_NEW_MESSAGE",
                    "roomId", room.getId(),
                    "fromUserId", String.valueOf(sender.getUserId()),
                    "fromNickname", sender.getNickname()
            );

            for (ChatUserSummary p : participants) {
                try {
                    if (p.getUserId() == null) continue;
                    if (Objects.equals(p.getUserId(), sender.getUserId())) continue;
                    if (p.getAuthority() != Authority.ADMIN && p.getAuthority() != Authority.INQUIRY) continue;

                    // 운영진 단체방은 CHAT 카테고리로 저장/표시 추천
                    pushService.sendToUser(p.getUserId(), title, body, data, NotificationCategory.CHAT, true);
                } catch (Exception e) {
                    log.warn("[InquiryNotify] STAFF_GROUP 알림 실패 roomId={}, toUserId={}", room.getId(), p.getUserId(), e);
                }
            }
            return;
        }


        List<ChatUserSummary> customers = participants.stream()
                .filter(u -> u.getAuthority() == Authority.USER)
                .toList();

        List<ChatUserSummary> handlers = participants.stream()
                .filter(u -> u.getAuthority() == Authority.INQUIRY || u.getAuthority() == Authority.ADMIN)
                .toList();

        boolean senderIsCustomer = sender.getAuthority() == Authority.USER;
        boolean senderIsHandler = sender.getAuthority() == Authority.INQUIRY || sender.getAuthority() == Authority.ADMIN;

        if (!senderIsCustomer && !senderIsHandler) return;

        if (senderIsCustomer) {
            // 고객 -> 운영진 전체
            String title = "새 문의 메시지";
            String body = sender.getNickname() + "님의 새 문의 메시지: " + snippet;

            Map<String, String> data = Map.of(
                    "type", "INQUIRY_NEW_MESSAGE",
                    "roomId", room.getId(),
                    "fromUserId", String.valueOf(sender.getUserId()),
                    "fromNickname", sender.getNickname()
            );

            for (ChatUserSummary h : handlers) {
                try {
                    if (h.getUserId() == null) continue;
                    if (Objects.equals(h.getUserId(), sender.getUserId())) continue;

                    pushService.sendToUser(h.getUserId(), title, body, data, NotificationCategory.INQUIRY, true);
                } catch (Exception e) {
                    log.warn("[InquiryNotify] 고객->운영진 알림 실패 roomId={}, handlerId={}", room.getId(), h.getUserId(), e);
                }
            }
            return;
        }

        // 운영진(ADMIN/INQUIRY) -> 고객 + 다른 운영진(본인 제외)
        String title = "문의 답변이 도착했습니다";
        String body = "담당자의 답변: " + snippet;

        Map<String, String> data = Map.of(
                "type", "INQUIRY_REPLY",
                "roomId", room.getId(),
                "fromUserId", String.valueOf(sender.getUserId()),
                "fromNickname", sender.getNickname()
        );
        //  고객
        for (ChatUserSummary c : customers) {
            try {
                if (c.getUserId() == null) continue;
                pushService.sendToUser(c.getUserId(), title, body, data, NotificationCategory.INQUIRY, true);
            } catch (Exception e) {
                log.warn("[InquiryNotify] 운영진->고객 알림 실패 roomId={}, customerId={}", room.getId(), c.getUserId(), e);
            }
        }
        // 다른 운영진에게도(관리자 모니터링)
        String staffTitle = "문의방 새 메시지";
        String staffBody = sender.getNickname() + " : " + snippet;

        for (ChatUserSummary h : handlers) {
            try {
                if (h.getUserId() == null) continue;
                if (Objects.equals(h.getUserId(), sender.getUserId())) continue;

                pushService.sendToUser(h.getUserId(), staffTitle, staffBody, data, NotificationCategory.INQUIRY, true);
            } catch (Exception e) {
                log.warn("[InquiryNotify] 운영진->운영진(모니터링) 알림 실패 roomId={}, handlerId={}", room.getId(), h.getUserId(), e);
            }
        }
    }
    private String safeNick(ChatUserSummary u) {
        if (u == null) return "";
        if (u.getNickname() != null && !u.getNickname().isBlank()) return u.getNickname();
        if (u.getEmail() != null) return u.getEmail();
        return "알 수 없음";
    }

    private String trimPreview(String text) {
        if (text == null) return "";
        int limit = 30;
        if (text.length() <= limit) return text;
        return text.substring(0, limit) + "...";
    }
}