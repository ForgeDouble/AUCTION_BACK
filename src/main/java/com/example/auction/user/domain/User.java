package com.example.auction.user.domain;


import com.example.auction.common.domain.BaseTimeEntity;
import com.example.auction.user.dto.UserUpdateDto;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Column(length = 100)
    private String address;

    @Column(nullable = false, length = 20)
    private String birthday; // xxxx.xx.xx

    @Column(nullable = false, length = 11)
    private String phone; // xxx-xxxx-xxxx

    @Builder.Default
    @Column(nullable = false)
    private Long warning = 0L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Authority authority;

    @Column(unique = true, length = 30)
    private String nickname;

    // 닉네임 변경 관련
    private LocalDateTime lastNicknameChangedAt;

    // 정지 종료 시각
    private LocalDateTime suspendedUntil;

    // 임시 잠금
    @Builder.Default
    @Column(nullable = false)
    private Boolean viewOnly = false;

    @Column(length = 500)
    private String profileImageUrl;

    @Column(length = 500)
    private String profileImageKey;

//    private String profileImage;
    @Builder.Default
    @Column(nullable = false)
    private Boolean birthdayCalendarEnabled = false;

    public void update(UserUpdateDto dto) {
        if (dto.getNickname() != null && !dto.getNickname().isBlank()) {
            this.name = dto.getNickname().trim();
        }
        if (dto.getAddress() != null && !dto.getAddress().isBlank()) {
            this.address = dto.getAddress().trim();
        }
        if (dto.getPhone() != null && !dto.getPhone().isBlank()) {
            this.phone = dto.getPhone().trim();
        }
    }

    // 닉네임 변경 메서드
    public void changeNickname(String newNickname) {
        this.nickname = newNickname;
        this.lastNicknameChangedAt = LocalDateTime.now();
    }
    // 임시정지 활성화
    public void makeViewOnly() { this.viewOnly = true; }

    // 임시정지 해제 ( 관리자 취소 경우 / 임계치 이하 도달 시 )
    public void cancelViewOnly() { this.viewOnly = false; }

    // 확정 정지
    public void suspendUntil(LocalDateTime until) { this.suspendedUntil = until; }
    // 확정 정지 해제
    public void liftSuspension() { this.suspendedUntil = null; }
    // 생일 이벤트 생성
    public void enableBirthdayCalendar() { this.birthdayCalendarEnabled = true; }
    // 생일 이벤트 삭제
    public void disableBirthdayCalendar() { this.birthdayCalendarEnabled = false; }
}
