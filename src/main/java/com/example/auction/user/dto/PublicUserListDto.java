package com.example.auction.user.dto;

import com.example.auction.user.domain.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
public class PublicUserListDto {
    private String nickname;
    private Long warning;
    private LocalDateTime createdAt;

    // 거래횟수

    public static PublicUserListDto fromEntityForPublic(User user) {
        return PublicUserListDto.builder()
                .nickname(user.getNickname())
                .warning(user.getWarning())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
