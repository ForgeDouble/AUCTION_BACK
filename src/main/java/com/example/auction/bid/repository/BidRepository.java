package com.example.auction.bid.repository;

import com.example.auction.bid.domain.Bid;
import com.example.auction.bid.domain.IsWinned;
import com.example.auction.bid.dto.BidAllByUserDto;
import com.example.auction.product.domain.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BidRepository extends JpaRepository<Bid, Long> {
    List<Bid> findAllByProduct_ProductIdOrderByCreatedAtDesc(Long productId);
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
    Page<BidAllByUserDto> findBidAllByUser(@Param("email") String email, Pageable pageable);

    // 전체 입찰 개수
    long count();

    // 금일 입찰 개수
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // 금일 총 거래 금액
    @Query("""
        select coalesce(sum(b.bidAmount), 0)
        from Bid b
        join b.product p
        where b.isWinned = :winned
          and p.status = :soldStatus
          and p.updatedAt >= :start and p.updatedAt < :end
          and p.blocked = false
    """)
    long sumWinningAmountForSoldProductsBetween(
            @Param("winned") IsWinned winned,
            @Param("soldStatus") Status soldStatus,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    interface MonthlyTotalProjection {
        String getYm();     // 2025-12
        Long getTotal();    // sum
    }

    @Query("""
        select function('date_format', p.updatedAt, '%Y-%m') as ym,
               coalesce(sum(b.bidAmount), 0) as total
        from Bid b
        join b.product p
        where b.isWinned = :winned
          and p.status = :soldStatus
          and p.updatedAt >= :from and p.updatedAt < :to
          and p.blocked = false
        group by function('date_format', p.updatedAt, '%Y-%m')
        order by function('date_format', p.updatedAt, '%Y-%m')
    """)
    List<MonthlyTotalProjection> findMonthlyWinningTotals(
            @Param("winned") IsWinned winned,
            @Param("soldStatus") Status soldStatus,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
