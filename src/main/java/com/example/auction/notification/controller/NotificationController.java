package com.example.auction.notification.controller;

import com.example.auction.common.dto.CommonResDto;
import com.example.auction.notification.domain.NotificationCategory;
import com.example.auction.notification.dto.NotificationResponseDto;
import com.example.auction.notification.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@RestController
@RequestMapping("/notifications")

public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<List<NotificationResponseDto>> list(
            @RequestParam(name = "category", required = false) NotificationCategory category,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        List<NotificationResponseDto> list = notificationService.listMyNotifications(category, page, size);
        return ResponseEntity.ok(list);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/unread-count")
    public ResponseEntity<CommonResDto> unreadCount() {
        long count = notificationService.countMyUnread();
        return ResponseEntity.ok(
                new CommonResDto(HttpStatus.OK, "알림 미읽음 수 조회 성공", count)
        );
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/read")
    public ResponseEntity<CommonResDto> markRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok(
                new CommonResDto(HttpStatus.OK, "알림 읽음 처리 완료", null)
        );
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/read-all")
    public ResponseEntity<CommonResDto> markAllRead() {
        notificationService.markAllAsReadForMe();
        return ResponseEntity.ok(
                new CommonResDto(HttpStatus.OK, "전체 알림 읽음 처리 완료", null)
        );
    }
}
