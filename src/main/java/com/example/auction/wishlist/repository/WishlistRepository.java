package com.example.auction.wishlist.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.auction.wishlist.domain.Wishlist;

import java.util.Optional;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {
    Optional<Wishlist> findByUser_UserId(Long userId);

    // 사용자 중복 제어
    boolean existsByUser_UserIdAndProduct_ProductId(Long userId, Long productId);
    Optional<Wishlist> findByWishlistId(Long wishlistId);
}
