package com.example.auction.wishlist.dto;


import com.example.auction.wishlist.domain.Wishlist;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WishlistAllDto {
    private Long whishlistId;
    private Long userId;
    private Long productId;

    public static WishlistAllDto fromEntity(Wishlist wishlist) {
        return WishlistAllDto.builder()
                .whishlistId(wishlist.getWhishlistId())
                .userId(wishlist.getUser().getUserId())
                .productId(wishlist.getProduct().getProductId())
                .build();
    }
}


