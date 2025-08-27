package com.example.auction.bid.dto;

import com.example.auction.bid.domain.Bid;
import com.example.auction.bid.domain.IsWinned;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BidCreateDto {
    private Long productId;
    private Long bidAmount;
    private IsWinned isWinned;

    public Bid toBid() {
        return Bid.builder()
                .bidAmount(bidAmount)
                .isWinned(isWinned)
                .build();
    }
}
