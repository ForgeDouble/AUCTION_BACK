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

//    @ManyToOne(fetch = FetchType.LAZY)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
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

    // 타입 값을 별도 컬럼으로 저장
    @Column(name = "notification_type", length = 80)
    private String notificationType;

    // data 전체(JSON) 저장
    @Lob
    @Column(name = "data_json", columnDefinition = "TEXT")
    private String dataJson;

    public void markRead() {
        this.read = true;
    }
}