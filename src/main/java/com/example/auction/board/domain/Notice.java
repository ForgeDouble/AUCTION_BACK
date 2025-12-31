package com.example.auction.board.domain;

import com.example.auction.common.domain.BaseTimeEntity;
import com.example.auction.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

// 인수인계(공지)
@Entity
@Table(name = "admin_notice", indexes = {
        @Index(name = "idx_notice_created", columnList = "created_at"),
        @Index(name = "idx_notice_category", columnList = "category"),
        @Index(name = "idx_notice_pinned", columnList = "pinned")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Notice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_user_id", nullable = false)
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NoticeCategory category;

    @Column(nullable = false, length = 200)
    private String title;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    @Builder.Default
    private boolean pinned = false;

    // 0~100 중요도 (필요 없으면 나중에 삭제 가능)
    @Column(nullable = false)
    @Builder.Default
    private int importance = 50;

    public void update(NoticeCategory category, String title, String content, Boolean pinned, Integer importance) {
        if (category != null) this.category = category;
        if (title != null) this.title = title;
        if (content != null) this.content = content;
        if (pinned != null) this.pinned = pinned;
        if (importance != null) this.importance = Math.max(0, Math.min(100, importance));
    }
}
