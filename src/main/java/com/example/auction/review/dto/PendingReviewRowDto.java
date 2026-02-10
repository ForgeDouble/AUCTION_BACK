package com.example.auction.review.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingReviewRowDto {

    private Long productId;
    private String productName;

    private Long sellerId;
    private String sellerNick;

    private Long winnerBidAmount;

    private LocalDateTime auctionEndTime;

}
