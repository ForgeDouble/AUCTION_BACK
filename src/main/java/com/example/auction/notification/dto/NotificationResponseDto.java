package com.example.auction.notification.dto;

import com.example.auction.notification.domain.Notification;
import com.example.auction.notification.domain.NotificationCategory;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationResponseDto {

    private Long id;
    private String title;
    private String body;
    private NotificationCategory category;
    private String createdAt;
    private boolean read;

    public static NotificationResponseDto fromEntity(Notification notification) {
        return NotificationResponseDto.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .body(notification.getBody())
                .category(notification.getCategory())
                .createdAt(notification.getCreatedAt() != null ? notification.getCreatedAt().toString() : null)
                .read(notification.isRead())
                .build();
    }
}