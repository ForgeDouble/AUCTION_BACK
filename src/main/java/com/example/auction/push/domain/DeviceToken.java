package com.example.auction.push.domain;


import com.example.auction.common.domain.BaseTimeEntity;
import com.example.auction.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "device_token", indexes = {
        @Index(name="idx_device_token_user", columnList="user_id"),
        @Index(name="idx_device_token_token", columnList="token", unique=true)
})
public class DeviceToken extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id", nullable=false)
    private User user;

    @Column(nullable=false, length=500, unique=true)
    @Convert(converter = AesGcmStringConverter.class)
    private String token;

    @Enumerated(EnumType.STRING) @Column(nullable=false, length=10)
    private DevicePlatform platform;

    @Builder.Default
    @Column(nullable=false)
    private boolean valid = true;

    private String appVersion;
    private String deviceModel;
}