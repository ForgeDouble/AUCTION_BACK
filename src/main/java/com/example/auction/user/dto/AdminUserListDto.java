package com.example.auction.user.dto;

import com.example.auction.user.domain.Gender;
import com.example.auction.user.domain.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
public class AdminUserListDto {
    private String nickname;
    private Gender gender;
    private String phone;
    private Long warning;
    private LocalDateTime createdAt;

    // 댓글내역
    // 거래내역
    // 작성상품 내역
    public static AdminUserListDto fromEntityForAdmin(User user) {
        return AdminUserListDto.builder()
                .nickname(user.getNickname())
                .gender(user.getGender())
                .phone(user.getPhone())
                .warning(user.getWarning())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
