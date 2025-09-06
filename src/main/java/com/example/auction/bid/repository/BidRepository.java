package com.example.auction.bid.repository;

import com.example.auction.bid.domain.Bid;
import com.example.auction.bid.domain.IsWinned;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BidRepository extends JpaRepository<Bid, Long> {
    List<Bid> findAllByProduct_ProductId(Long productId);
    Optional<Bid> findByProduct_ProductIdAndIsWinned(Long productId, IsWinned isWinned);
    Optional<Bid> findTopByProduct_ProductIdOrderByCreatedAtDesc(Long productId);
}
