package com.example.auction.bid.dto;

import com.example.auction.bid.domain.IsWinned;
import com.example.auction.product.domain.Status;
import com.example.auction.product.dto.ProductImageDto;

import java.time.LocalDateTime;

public record BidAllByUserDto(
        Long bidId,
        Long productId,
        String productName,
        Long bidAmount,
        LocalDateTime bidCreatedAt,
        Long imgId,
        String imgUrl,
        Integer imgPosition,
        IsWinned isWinned,
        Status status,
        LocalDateTime productCreatedAt
) {

}
