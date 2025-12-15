package com.example.auction.bid.dto;

import com.example.auction.bid.domain.Bid;
import com.example.auction.bid.domain.IsWinned;
import com.example.auction.common.domain.DelYN;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BidAllDto {

    private Long userId;
    private String userName;
    private Long productId;
    private Long bidAmount;
    private IsWinned isWinned;
    private LocalDateTime createdAt;

    public static BidAllDto fromEntity(Bid bid) {
        return BidAllDto.builder()
                .userId(bid.getUser().getUserId())
                .userName(bid.getUser().getName())
                .productId(bid.getProduct().getProductId())
                .bidAmount(bid.getBidAmount())
                .isWinned(bid.getIsWinned())
                .createdAt(bid.getCreatedAt())
                .build();
    }
}
