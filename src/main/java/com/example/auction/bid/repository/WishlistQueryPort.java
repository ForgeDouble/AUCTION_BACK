package com.example.auction.bid.repository;

import java.util.Set;

public interface WishlistQueryPort {
    Set<Long> findWishlisterUserIdsByProductId(Long productId);
}
