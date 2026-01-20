package com.example.auction.wishlist.repository;

import com.example.auction.bid.repository.WishlistQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class WishlistQueryAdapter implements WishlistQueryPort {

    private final WishlistRepository wishlistRepository;

    @Override
    public Set<Long> findWishlisterUserIdsByProductId(Long productId) {
        return wishlistRepository.findUserIdsByProductId(productId);
    }
}
