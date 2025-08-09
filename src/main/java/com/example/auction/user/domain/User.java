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

    @Column(nullable = false)
    private Long warning = 0L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Authority authority;

    @Column(unique = true, length = 30)
    private String nickname;

    private LocalDateTime lastNicknameChangedAt;

//    private String profileImage;

    public void update(UserUpdateDto dto) {
        this.name = dto.getName();
        this.gender = dto.getGender();
        this.address = dto.getAddress();
        this.birthday = dto.getBirthday();
        this.phone = dto.getPhone();
    }

    // 닉네임 변경 메서드
    public void changeNickname(String newNickname) {
        this.nickname = newNickname;
        this.lastNicknameChangedAt = LocalDateTime.now();
    }
}
