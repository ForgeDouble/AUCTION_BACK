package com.example.auction.notification.dto;

import com.example.auction.notification.domain.Notification;
import com.example.auction.notification.domain.NotificationCategory;
import com.example.auction.notification.util.NotificationPayloadJson;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class NotificationResponseDto {

    private Long id;
    private String title;
    private String body;
    private NotificationCategory category;
    private String createdAt;
    private boolean read;
    private String type;
    private Map<String, String> data;

    public static NotificationResponseDto fromEntity(Notification notification) {
        Map<String, String> data = NotificationPayloadJson.fromJson(notification.getDataJson());

        String type = notification.getNotificationType();
        if ((type == null || type.isBlank()) && data != null) {
            type = data.get("type");
        }
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