package com.example.auction.bid.dto;

import com.example.auction.bid.domain.Bid;
import com.example.auction.bid.domain.IsWinned;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BidWinnerDto {
    private Long bidId;
    private Long userId;
    private String userName;
    private Long productId;
    private String productName;
    private Long bidAmount;
    private IsWinned isWinned;
    private LocalDateTime createdAt;

    public static BidWinnerDto fromEntity(Bid bid) {
        return BidWinnerDto.builder()
                .bidId(bid.getBidId())
                .userId(bid.getUser().getUserId())
                .userName(bid.getUser().getName())
                .productId(bid.getProduct().getProductId())
                .productName(bid.getProduct().getProductName())
                .bidAmount(bid.getBidAmount())
                .isWinned(bid.getIsWinned())
                .createdAt(bid.getCreatedAt())
                .build();
    }

}
