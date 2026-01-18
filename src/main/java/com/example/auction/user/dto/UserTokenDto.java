package com.example.auction.user.dto;

import com.example.auction.user.domain.Authority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@AllArgsConstructor
@Getter
@Builder
public class UserTokenDto {
    private String email;
    private Authority authority;
}
