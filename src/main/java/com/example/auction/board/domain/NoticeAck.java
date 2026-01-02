package com.example.auction.board.domain;

import com.example.auction.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 인수인계 확인용
@Entity
@Table(name = "admin_notice_ack",
        uniqueConstraints = @UniqueConstraint(name = "uk_notice_ack", columnNames = {"notice_id", "user_id"}),
        indexes = {
                @Index(name = "idx_notice_ack_notice", columnList = "notice_id"),
                @Index(name = "idx_notice_ack_user", columnList = "user_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class NoticeAck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_ack_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notice_id", nullable = false)
    private Notice notice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static NoticeAck of(Notice notice, User user, LocalDateTime createdAt) {
        return NoticeAck.builder()
                .notice(notice)
                .user(user)
                .createdAt(createdAt)
                .build();
    }
}
