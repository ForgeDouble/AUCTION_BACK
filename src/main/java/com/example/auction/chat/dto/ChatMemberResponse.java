package com.example.auction.chat.dto;

import com.example.auction.user.domain.Authority;
import lombok.Builder;
import lombok.Data;

//
@Data
@Builder
public class ChatMemberResponse {
    private String userId;
    private String nickname;
    private String profileImageUrl;
    private Authority authority;
}