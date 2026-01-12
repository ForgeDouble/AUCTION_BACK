package com.example.auction.user.dto;

public record TokenExtendRes(
        String accessToken,
        long expiresInSec
) {}
