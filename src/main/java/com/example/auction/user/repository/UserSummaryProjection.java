package com.example.auction.user.repository;

import com.example.auction.user.domain.Authority;

public interface UserSummaryProjection {
    Long getUserId();
    String getEmail();
    String getNickname();
    Authority getAuthority();
    String getProfileImageUrl();
}
