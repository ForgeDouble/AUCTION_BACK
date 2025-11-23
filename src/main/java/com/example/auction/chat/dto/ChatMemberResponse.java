package com.example.auction.chat.dto;

import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class ChatMemberResponse {
    private Long userId;
    private String email;
    private String nickname;
    private String profileImageUrl;
    private Authority authority;

    public static ChatMemberResponse fromEntity(User user) {
        return ChatMemberResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .profileImageUrl(user.getProfileImageUrl())
                .authority(user.getAuthority())
                .build();
    }
}