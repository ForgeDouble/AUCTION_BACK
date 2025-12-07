package com.example.auction.common.auth;

import com.example.auction.user.domain.Authority;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class JwtPrincipal {
    private final Long userId;
    private final String email;
    private final Authority authority;
    private final String nickname;
}
