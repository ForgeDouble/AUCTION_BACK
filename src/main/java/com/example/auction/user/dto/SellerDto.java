package com.example.auction.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SellerDto {
    private Long userId;
    private String nickname;
    private String profileImageUrl;
    private LocalDateTime createdAt;
    private Long selledBidCount;
}
