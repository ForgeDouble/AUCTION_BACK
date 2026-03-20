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
    List<Bid> findAllByOrderByBidIdAsc();

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
    AND (:statuses IS NULL OR p.status IN :statuses)
    AND (:isWinned IS NULL OR b.isWinned = :isWinned)
    ORDER BY b.createdAt DESC
""")
    Page<BidAllByUserDto> findBidAllByUser(@Param("email") String email,
                                           @Param("statuses") List<Status> statuses,
                                           @Param("isWinned") IsWinned isWinned,
                                           Pageable pageable);

    Optional<Bid> findBidByUuid(String uuid);

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


    interface BidCountRow {
        Long getProductId();
        Long getCnt();
    }

    interface BidMaxRow {
        Long getProductId();
        Long getMaxAmount();
    }

    @Query("""
        select b.product.productId as productId, count(b) as cnt
        from Bid b
        where b.product.productId in :productIds
        group by b.product.productId
    """)
    List<BidCountRow> countByProductIds(@Param("productIds") List<Long> productIds);

    @Query("""
        select b.product.productId as productId, max(b.bidAmount) as maxAmount
        from Bid b
        where b.product.productId in :productIds
        group by b.product.productId
    """)
    List<BidMaxRow> maxBidAmountByProductIds(@Param("productIds") List<Long> productIds);



    @Query("""
select b
from Bid b
where b.isWinned = com.example.auction.bid.domain.IsWinned.Y
and b.user.userId = :userId
and b.product.status = com.example.auction.product.domain.Status.SELLED
and b.product.delYn = com.example.auction.common.domain.DelYN.N
and b.product.blocked = false
and not exists (
select 1
from Review r
where r.delYn = com.example.auction.common.domain.DelYN.N
and r.product.productId = b.product.productId
and r.reviewer.userId = :userId
)
order by b.createdAt desc
""")
    Page<Bid> findPendingReviewBids(@Param("userId") Long userId, Pageable pageable);


    // season에 적용시킬 코드
    interface UserLongRow {
        Long getUserId();
        Long getV();
    }

    @Query("""
    select b.user.userId as userId, count(b) as v
    from Bid b
    join b.product p
    where b.isWinned = com.example.auction.bid.domain.IsWinned.Y
      and p.status = com.example.auction.product.domain.Status.SELLED
      and p.updatedAt >= :start and p.updatedAt < :end
      and p.delYn = com.example.auction.common.domain.DelYN.N
      and p.blocked = false
      and b.user.delYn = com.example.auction.common.domain.DelYN.N
    group by b.user.userId
    order by count(b) desc
""")
    List<UserLongRow> countWinningByBuyerBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
    select b.user.userId as userId, coalesce(sum(b.bidAmount), 0) as v
    from Bid b
    join b.product p
    where b.isWinned = com.example.auction.bid.domain.IsWinned.Y
      and p.status = com.example.auction.product.domain.Status.SELLED
      and p.updatedAt >= :start and p.updatedAt < :end
      and p.delYn = com.example.auction.common.domain.DelYN.N
      and p.blocked = false
      and b.user.delYn = com.example.auction.common.domain.DelYN.N
    group by b.user.userId
    order by coalesce(sum(b.bidAmount), 0) desc
""")
    List<UserLongRow> sumWinningAmountByBuyerBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    // 경매왕: 월간 참여
    @Query("""
    select b.user.userId as userId, count(distinct b.product.productId) as v
    from Bid b
    join b.product p
    where b.createdAt >= :start and b.createdAt < :end
      and p.delYn = com.example.auction.common.domain.DelYN.N
      and p.blocked = false
      and b.user.delYn = com.example.auction.common.domain.DelYN.N
    group by b.user.userId
    order by count(distinct b.product.productId) desc
""")
    List<UserLongRow> countDistinctProductsBidBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    // 저격왕용 분모
    @Query("""
    select b.user.userId as userId, count(distinct p.productId) as v
    from Bid b
    join b.product p
    where p.status = com.example.auction.product.domain.Status.SELLED
      and p.updatedAt >= :start and p.updatedAt < :end
      and p.delYn = com.example.auction.common.domain.DelYN.N
      and p.blocked = false
      and b.user.delYn = com.example.auction.common.domain.DelYN.N
    group by b.user.userId
""")
    List<UserLongRow> countDistinctEndedSoldProductsParticipatedBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

}
