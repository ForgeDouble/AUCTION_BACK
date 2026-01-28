package com.example.auction.bid.dto;

import com.example.auction.bid.domain.IsWinned;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BidEvent {
    private String uuid;
    private Long userId;
    private String userNickName;
    private Long productId;
    private Long bidAmount;
    private LocalDateTime createdAt;
    private IsWinned isWinned;
}
