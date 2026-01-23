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
        img.id,
        img.url,
        img.position,
        b.isWinned,
        p.status,
        p.createdAt
    )
    FROM Bid b
    JOIN b.product p
    LEFT JOIN ProductImage img ON img.product.productId = p.productId
        AND img.position = (
            SELECT MIN(img2.position)
            FROM ProductImage img2
            WHERE img2.product.productId = p.productId
        )
    WHERE b.user.email = :email
    AND (:status IS NULL OR p.status = :status)
    ORDER BY b.createdAt DESC
""")
    Page<BidAllByUserDto> findBidAllByUser(@Param("email") String email,
                                           @Param("status") Status status,
                                           Pageable pageable);

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
        String getYm();
        Long getTotal();
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


    // 금일 거래 모니터링 (1. 금일 2. 월별 )
    @Query("""
        select coalesce(sum(b.bidAmount), 0)
        from Bid b
        join b.product p
        where p.status = com.example.auction.product.domain.Status.SELLED
          and p.updatedAt >= :start
          and p.updatedAt < :end
          and b.isWinned = com.example.auction.bid.domain.IsWinned.Y
    """)
    Long sumTodayGmv(@Param("start") LocalDateTime start,
                     @Param("end") LocalDateTime end);

    @Query("""
        select function('year', p.updatedAt) as yy,
               function('month', p.updatedAt) as mm,
               coalesce(sum(b.bidAmount), 0) as total
        from Bid b
        join b.product p
        where p.status = com.example.auction.product.domain.Status.SELLED
          and p.updatedAt >= :start
          and p.updatedAt < :end
          and b.isWinned = com.example.auction.bid.domain.IsWinned.Y
        group by function('year', p.updatedAt), function('month', p.updatedAt)
        order by function('year', p.updatedAt), function('month', p.updatedAt)
    """)
    List<Object[]> sumMonthlyGmv(@Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end);

}
