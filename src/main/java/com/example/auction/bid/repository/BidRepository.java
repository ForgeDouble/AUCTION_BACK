package com.example.auction.bid.repository;

import com.example.auction.bid.domain.Bid;
import com.example.auction.bid.domain.IsWinned;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BidRepository extends JpaRepository<Bid, Long> {
    List<Bid> findAllByProduct_ProductId(Long productId);
    Bid findByProduct_ProductIdAndIsWinned(Long productId, IsWinned isWinned);
}
