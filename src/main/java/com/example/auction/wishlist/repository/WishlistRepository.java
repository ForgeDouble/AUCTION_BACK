package com.example.auction.wishlist.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.auction.wishlist.domain.Wishlist;

import java.util.Optional;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {
    Optional<Wishlist> findByUser_UserId(Long userId);

}
