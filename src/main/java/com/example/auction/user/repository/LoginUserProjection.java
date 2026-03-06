package com.example.auction.user.repository;

import com.example.auction.common.domain.DelYN;
import com.example.auction.user.domain.Authority;

import java.time.LocalDateTime;

public interface LoginUserProjection {
    Long getUserId();
    String getEmail();
    String getPassword();
    Authority getAuthority();
    String getNickname();
    String getProfileImageUrl();
    DelYN getDelYn();
    LocalDateTime getSuspendedUntil();
}
