// src/main/java/com/example/auction/notification/service/NotificationService.java
package com.example.auction.notification.service;

import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.InternalErrorException;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.common.exception.UnauthorizedAccessException;
import com.example.auction.notification.domain.Notification;
import com.example.auction.notification.domain.NotificationCategory;
import com.example.auction.notification.dto.NotificationResponseDto;
import com.example.auction.notification.repository.NotificationRepository;
import com.example.auction.notification.util.NotificationPayloadJson;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private User currentUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            if (auth == null || auth.getName() == null || auth.getName().isBlank() || "anonymousUser".equals(auth.getName())) {
                log.warn("[AUTH_REQUIRED] 인증 정보 없음");
                throw new UnauthorizedAccessException("AUTH_REQUIRED", "로그인이 필요합니다.");
            }

            String email = auth.getName();

            return userRepository.findByEmailAndDelYn(email, DelYN.N)
                    .orElseThrow(() -> {
                        log.warn("[USER_NOT_FOUND] 현재 로그인 유저 조회 실패 email={}", email);
                        return new ResourceNotFoundException(
                                "USER_NOT_FOUND",
                                "현재 로그인한 유저를 찾을 수 없습니다. 고객센터에 문의해주세요."
                        );
                    });

        } catch (UnauthorizedAccessException | ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("[INTERNAL_SERVER_ERROR] currentUser 처리 중 예외", e);
            throw new InternalErrorException(
                    "INTERNAL_SERVER_ERROR",
                    "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
            );
        }
    }

    private void broadcast(User user, NotificationResponseDto dto) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) return;
        try {
            messagingTemplate.convertAndSend("/topic/notification/" + user.getEmail(), dto);
        } catch (Exception e) {
            log.warn("[NOTIFICATION_STOMP_FAIL] STOMP 전송 실패 userId={}, email={}",
                    user.getUserId(), user.getEmail(), e);
        }
    }

    @Transactional
    public void createAndSend(Long userId, NotificationCategory category, String title, String body) {

        if (userId == null) {
            log.warn("[NOTIFICATION_BAD_REQUEST] userId null - createAndSend skip title={}", title);
            return;
        }

        User user;
        try {
            user = userRepository.findById(userId)
                    .filter(u -> u.getDelYn() == DelYN.N)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("[NOTIFICATION_USER_LOOKUP_FAIL] 대상 유저 조회 실패 userId={}", userId, e);
            return;
        }

        if (user == null) {
            log.warn("[NOTIFICATION_USER_NOT_FOUND] 대상 유저 없음 userId={}", userId);
            return;
        }

        try {
            Notification notification = Notification.builder()
                    .user(user)
                    .category(category)
                    .title(title)
                    .body(body)
                    .read(false)
                    .build();

            Notification saved = notificationRepository.save(notification);
            NotificationResponseDto dto = NotificationResponseDto.fromEntity(saved);

            broadcast(user, dto);

        } catch (Exception e) {
            log.warn("[NOTIFICATION_CREATE_FAIL] 알림 저장/전송 실패 userId={}, title={}", userId, title, e);
        }
    }

    // 내부 호출용: data 포함 버전
    @Transactional
    public NotificationResponseDto createAndSend(Long userId,
                                                 NotificationCategory category,
                                                 String title,
                                                 String body,
                                                 Map<String, String> data) {

        if (userId == null) {
            log.warn("[NOTIFICATION_BAD_REQUEST] userId null - createAndSend(data) skip title={}", title);
            return null;
        }

        User user;
        try {
            user = userRepository.findById(userId)
                    .filter(u -> u.getDelYn() == DelYN.N)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("[NOTIFICATION_USER_LOOKUP_FAIL] 대상 유저 조회 실패 userId={}", userId, e);
            return null;
        }

        if (user == null) {
            log.warn("[NOTIFICATION_USER_NOT_FOUND] 대상 유저 없음 userId={}", userId);
            return null;
        }

        try {
            String type = (data != null) ? data.get("type") : null;
            String dataJson = NotificationPayloadJson.toJson(data);

            Notification notification = Notification.builder()
                    .user(user)
                    .category(category)
                    .title(title)
                    .body(body)
                    .read(false)
                    .notificationType(type)
                    .dataJson(dataJson)
                    .build();

            Notification saved = notificationRepository.save(notification);
            NotificationResponseDto dto = NotificationResponseDto.fromEntity(saved);

            broadcast(user, dto);
            return dto;

        } catch (Exception e) {
            log.warn("[NOTIFICATION_CREATE_FAIL] 알림 저장/전송 실패 userId={}, title={}", userId, title, e);
            return null;
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationResponseDto> listMyNotifications(NotificationCategory category, int page, int size) {
        User user = currentUser();

        int pg = Math.max(page, 0);
        int sz = Math.min(Math.max(size, 1), 50);
        PageRequest pageRequest = PageRequest.of(pg, sz);

        try {
            Page<Notification> result;
            if (category == null) {
                result = notificationRepository.findByUserAndDelYnOrderByCreatedAtDesc(user, DelYN.N, pageRequest);
            } else {
                result = notificationRepository.findByUserAndCategoryAndDelYnOrderByCreatedAtDesc(
                        user, category, DelYN.N, pageRequest
                );
            }

            return result.stream()
                    .map(NotificationResponseDto::fromEntity)
                    .toList();

        } catch (Exception e) {
            log.error("[NOTIFICATION_LIST_FAILED] 알림 목록 조회 실패 userId={}, email={}, category={}, page={}, size={}",
                    user.getUserId(), user.getEmail(), category, pg, sz, e);

            throw new InternalErrorException(
                    "NOTIFICATION_LIST_FAILED",
                    "알림을 불러오지 못했습니다. 잠시 후 다시 시도해주세요."
            );
        }

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
                .orElseThrow(() -> {
                    log.warn("[NOTIFICATION_NOT_FOUND] 알림 없음 id={}", id);
                    return new ResourceNotFoundException("NOTIFICATION_NOT_FOUND", "알림이 존재하지 않습니다.");
                });

        if (!notification.getUser().getUserId().equals(me.getUserId())) {
            log.warn("[UNAUTHORIZED_ACCESS] 다른 사용자의 알림 접근 me={}, owner={}, id={}",
                    me.getUserId(), notification.getUser().getUserId(), id);
            throw new UnauthorizedAccessException("UNAUTHORIZED_ACCESS", "다른 사용자의 알림입니다.");
        }

        notification.markRead();
    }

    @Transactional
    public void markAllAsReadForMe() {
        User user = currentUser();
        notificationRepository.markAllReadByUser(user, DelYN.N);
    }
}