package com.example.auction.user.dto;

import com.example.auction.user.domain.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserStatusDto {
    private String email;
    private UserStatus status;
}
