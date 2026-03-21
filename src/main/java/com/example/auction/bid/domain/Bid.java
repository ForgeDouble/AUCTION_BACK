package com.example.auction.bid.domain;

import com.example.auction.common.domain.BaseTimeEntity;
import com.example.auction.product.domain.Product;
import com.example.auction.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
public class Bid extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bidId;

    @Column
    private String uuid;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private Long bidAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IsWinned isWinned;

    @Column
    private Boolean isFirst = false;

    public void updateBidAmount(Long bidAmount) {
        if (bidAmount != null) {
            this.bidAmount = bidAmount;
        }
    }
}
