package com.example.auction.chat.dto;

import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatUserSummary {

    private Long userId;
    private String email;
    private String nickname;
    private Authority authority;
    private String profileImageUrl;

    public static ChatUserSummary from(User user) {
        if (user == null) return null;

        return ChatUserSummary.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .profileImageUrl(user.getProfileImageUrl())
                .authority(user.getAuthority())
                .build();
    }


}
