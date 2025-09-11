package com.example.auction.bid.dto;

import com.example.auction.bid.domain.IsWinned;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BidEvent {
    private Long userId;
    private Long productId;
    private Long bidAmount;
    private IsWinned isWinned;
}
