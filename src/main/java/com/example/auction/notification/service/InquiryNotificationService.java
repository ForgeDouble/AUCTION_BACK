package com.example.auction.notification.service;

import com.example.auction.chat.domain.ChatRoom;
import com.example.auction.chat.dto.ChatUserSummary;
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

    // 2) 문의방 내 새 메시지 알림 (유저 ↔ 담당자)
    public void notifyOnNewMessage(ChatRoom room, ChatUserSummary sender, String preview) {
        if (room == null || sender == null) {
            return;
        }

        // 일반 1:1 채팅에는 알림 안 보냄 → adminChat(문의/운영 채팅)에만 적용
        if (!room.isAdminChat()) {
            return;
        }

        Map<String, ChatUserSummary> map = chatUserCacheService.getByEmails(room.getParticipantIds());
        List<ChatUserSummary> participants = map.values().stream().toList();

        List<ChatUserSummary> customers = participants.stream()
                .filter(u -> u.getAuthority() == Authority.USER)
                .toList();

        List<ChatUserSummary> handlers = participants.stream()
                .filter(u -> u.getAuthority() == Authority.INQUIRY || u.getAuthority() == Authority.ADMIN)
                .toList();

        boolean senderIsCustomer = sender.getAuthority() == Authority.USER;
        boolean senderIsHandler = sender.getAuthority() == Authority.INQUIRY
                || sender.getAuthority() == Authority.ADMIN;

        if (!senderIsCustomer && !senderIsHandler) return;

        String snippet = trimPreview(preview);

        if (senderIsCustomer) {
            // 고객이 보낸 메시지 → 문의 담당자/운영자에게
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
                    pushService.sendToUser(h.getUserId(), title, body, data, NotificationCategory.INQUIRY, true);
                } catch (Exception e) {
                    log.warn("[InquiryNotify] 새 문의 메시지 알림 실패 roomId={}, handlerId={}",
                            room.getId(), h.getUserId(), e);
                }
            }
        } else if (senderIsHandler) {
            // 담당자/ADMIN 이 보낸 메시지 → 고객
            if (customers.isEmpty()) {
                return;
            }

            String title = "문의 답변이 도착했습니다";
            String body = "담당자의 답변: " + snippet;

            Map<String, String> data = Map.of(
                    "type", "INQUIRY_REPLY",
                    "roomId", room.getId(),
                    "fromUserId", String.valueOf(sender.getUserId()),
                    "fromNickname", sender.getNickname()
            );

            for (ChatUserSummary customer : customers) {
                try {
                    if (Objects.equals(customer.getUserId(), sender.getUserId())) {
                        continue;
                    }
                    pushService.sendToUser(customer.getUserId(), title, body, data, NotificationCategory.INQUIRY, true);
                } catch (Exception e) {
                    log.warn("[InquiryNotify] 문의 답변 알림 실패 roomId={}, customerId={}",
                            room.getId(), customer.getUserId(), e);
                }
            }
        }
    }

    private String trimPreview(String text) {
        if (text == null) return "";
        int limit = 30;
        if (text.length() <= limit) return text;
        return text.substring(0, limit) + "...";
    }
}