package com.example.auction.bid.dto;

import com.example.auction.bid.domain.IsWinned;

import java.time.LocalDateTime;

public record BidAllByUserDto(
        Long bidId,
        Long productId,
        String productName,
        Long bidAmount,
        LocalDateTime bidCreatedAt,
        IsWinned isWinned,
        LocalDateTime productCreatedAt
) {

}
