package com.example.auction.notification.service;

import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.notification.domain.Notification;
import com.example.auction.notification.domain.NotificationCategory;
import com.example.auction.notification.dto.NotificationResponseDto;
import com.example.auction.notification.repository.NotificationRepository;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException("현재 로그인한 유저를 찾을 수 없습니다."));
    }

    @Transactional
    public void createAndSend(Long userId, NotificationCategory category, String title, String body) {

        if (userId == null) {
            log.warn("[Notification] userId 가 없습니다. createAndSend 스킵 title={}", title);
            return;
        }

        User user = userRepository.findById(userId).filter(u -> u.getDelYn() == DelYN.N).orElse(null);
        if (user == null) {
            log.warn("[Notification] 대상 유저를 찾을 수 없습니다 userId={}", userId);
            return;
        }

        Notification notification = Notification.builder()
                .user(user)
                .category(category)
                .title(title)
                .body(body)
                .read(false)
                .build();

        Notification saved = notificationRepository.save(notification);

        NotificationResponseDto dto = NotificationResponseDto.fromEntity(saved);

        // STOMP 브로드캐스트
        try {
            messagingTemplate.convertAndSend("/topic/notification/" + user.getEmail(), dto);
        } catch (Exception e) {
            log.warn("[Notification] STOMP 전송 실패 userId={}", userId, e);
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationResponseDto> listMyNotifications(NotificationCategory category, int page, int size) {
        User user = currentUser();
        int pg = Math.max(page, 0);
        // 최대 50개 선택
        int sz = Math.min(Math.max(size, 1), 50);
        PageRequest pageRequest = PageRequest.of(pg, sz);

        Page<Notification> result;
        if (category == null) {
            result = notificationRepository.findByUserAndDelYnOrderByCreatedAtDesc(user, DelYN.N, pageRequest);
        } else {
            result = notificationRepository.findByUserAndCategoryAndDelYnOrderByCreatedAtDesc(user, category, DelYN.N, pageRequest);
        }

        return result.stream()
                .map(NotificationResponseDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countMyUnread() {
        User user = currentUser();
        return notificationRepository.countByUserAndDelYnAndReadIsFalse(user, DelYN.N);
    }

    @Transactional
    public void markAsRead(Long id) {
        User me = currentUser();
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("알림이 존재하지 않습니다."));

        if (!notification.getUser().getUserId().equals(me.getUserId())) {
            throw new IllegalStateException("다른 사용자의 알림입니다.");
        }
        notification.markRead();
    }

    @Transactional
    public void markAllAsReadForMe() {
        User user = currentUser();
        notificationRepository.markAllReadByUser(user, DelYN.N);
    }
}