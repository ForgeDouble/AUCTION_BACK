package com.example.auction.user.dto;

import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import lombok.Builder;

import java.time.LocalDateTime;
@Builder
public record AdminUserRowDto(
        Long userId,
        String email,
        String name,
        String nickname,
        String phone,
        Authority authority,
        Boolean viewOnly,
        LocalDateTime suspendedUntil,
        String profileImageUrl
) {
    public static AdminUserRowDto adminUserRowDto(User user) {
        return AdminUserRowDto.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .name(user.getName())
                .nickname(user.getNickname())
                .phone(user.getPhone())
                .authority(user.getAuthority())
                .viewOnly(user.getViewOnly())
                .suspendedUntil(user.getSuspendedUntil())
                .profileImageUrl(user.getProfileImageUrl())
                .build();
    }
}
