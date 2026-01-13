package com.example.auction.admin.dto;

public record AdminAuctionRowDto(
        String id,
        String title,
        String sellerMasked,
        String category,
        long currentBid,
        long bidCount,
        String endsAt,
        String status    // "READY" | "PROCESSING" | "SELLED" | "NOTSELLED" | "BLOCKED"
) { }
