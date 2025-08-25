package com.example.auction.wishlist.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.auction.wishlist.domain.Wishlist;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

}
