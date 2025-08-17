package com.example.auction.user.dto;

import com.example.auction.user.domain.Gender;
import com.example.auction.user.domain.User;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {
    private Long userId;
    private String email;
    private String name;
    private Gender gender;
    private String address;
    private String birthday;
    private String phone;
    private Long warning;
    private String nickname;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 나의 판매글내역
    // 나의 댓글 내역
    // 나의 거래(경매참여) 내역

    public static UserDto fromEntity(User user) {
        return UserDto.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .name(user.getName())
                .gender(user.getGender())
                .address(user.getAddress())
                .birthday(user.getBirthday())
                .phone(user.getPhone())
                .warning(user.getWarning())
                .nickname(user.getNickname())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
