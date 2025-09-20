package com.example.auction.push.domain;

import com.example.auction.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name="push_event", indexes = @Index(name="idx_push_event_user", columnList="user_id"))
public class PushEvent extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false)
    private Long userId;
    @Column(nullable=false, length=64)
    private String pushId;
    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=16)
    private PushEventType event;
}
