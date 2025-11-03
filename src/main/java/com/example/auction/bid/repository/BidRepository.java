package com.example.auction.bid.repository;

import com.example.auction.bid.domain.Bid;
import com.example.auction.bid.domain.IsWinned;
import com.example.auction.bid.dto.BidAllByUserDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BidRepository extends JpaRepository<Bid, Long> {
    List<Bid> findAllByProduct_ProductId(Long productId);
    Optional<Bid> findByProduct_ProductIdAndIsWinned(Long productId, IsWinned isWinned);
    Optional<Bid> findTopByProduct_ProductIdOrderByCreatedAtDesc(Long productId);

    @Query("""
        SELECT new com.example.auction.bid.dto.BidAllByUserDto(
            b.bidId,
            p.productId,
            p.productName,
            b.bidAmount,
            b.createdAt,
            b.isWinned,
            p.createdAt
        )
        FROM Bid b
        JOIN b.product p
        WHERE b.user.email = :email
        ORDER BY b.createdAt DESC
    """)
    List<BidAllByUserDto> findBidAllByUser(@Param("email") String email);
}
