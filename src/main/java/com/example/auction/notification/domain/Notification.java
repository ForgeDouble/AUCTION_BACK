package com.example.auction.notification.domain;

import com.example.auction.common.domain.BaseTimeEntity;
import com.example.auction.common.domain.DelYN;
import com.example.auction.user.domain.User;
import jakarta.persistence.*;
import lombok.*;


import java.time.Instant;

@Entity
@Table(name = "user_notification", indexes = {
        @Index(name = "idx_notification_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_notification_user_read", columnList = "user_id, read_flag")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationCategory category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(name = "read_flag", nullable = false)
    @Builder.Default
    private boolean read = false;

    public void markRead() {
        this.read = true;
    }
}