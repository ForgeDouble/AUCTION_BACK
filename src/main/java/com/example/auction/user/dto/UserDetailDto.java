package com.example.auction.user.dto;

import com.example.auction.user.domain.Gender;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDetailDto {
    private Long userId;
    private String email;
    private String name;
    private Gender gender;
    private String birthday;
    private String phone;
    private Authority authority;
    private Long warning;
    private String address;
    private String nickname;

    private String profileImageUrl;
    private LocalDateTime createdAt;

    public static UserDetailDto fromEntity(User user) {
        return UserDetailDto.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .name(user.getName())
                .gender(user.getGender())
                .birthday(user.getBirthday())
                .phone(user.getPhone())
                .authority(user.getAuthority())
                .warning(user.getWarning())
                .address(user.getAddress())
                .nickname(user.getNickname())
                .profileImageUrl(user.getProfileImageUrl())
                .createdAt(user.getCreatedAt())
                .build();
    }
}