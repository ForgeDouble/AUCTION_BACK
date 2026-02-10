package com.example.auction.review.repository;

import com.example.auction.common.domain.DelYN;
import com.example.auction.review.domain.Review;
import com.example.auction.review.domain.ReviewTag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByProduct_ProductIdAndReviewer_UserIdAndDelYn(Long productId, Long reviewerId, DelYN delYn);
    @EntityGraph(attributePaths = {"product", "reviewer"})
    Page<Review> findAllByProduct_ProductIdAndDelYnOrderByCreatedAtDesc(Long productId, DelYN delYn, Pageable pageable);
    @EntityGraph(attributePaths = {"product", "reviewer"})
    Page<Review> findAllBySeller_UserIdAndDelYnOrderByCreatedAtDesc(Long sellerId, DelYN delYn, Pageable pageable);
    @EntityGraph(attributePaths = {"product", "reviewer"})
    Page<Review> findAllByReviewer_UserIdAndDelYnOrderByCreatedAtDesc(Long reviewerId, DelYN delYn, Pageable pageable);


    interface SellerCountRow {
        Long getSellerId();
        Long getCnt();
    }

    // 판매자별 월간 총 리뷰 수 (차단/삭제 상품 제외)
    @Query("""
        select r.seller.userId as sellerId, count(r) as cnt
        from Review r
        join r.product p
        where r.delYn = com.example.auction.common.domain.DelYN.N
          and r.createdAt >= :start and r.createdAt < :end
          and p.delYn = com.example.auction.common.domain.DelYN.N
          and (p.blocked = false or p.blocked is null)
          and r.seller.delYn = com.example.auction.common.domain.DelYN.N
        group by r.seller.userId
    """)
    List<SellerCountRow> countMonthlyReviewsBySeller(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    // 특정 태그가 포함된 리뷰 수
    @Query("""
        select r.seller.userId as sellerId, count(r) as cnt
        from Review r
        join r.product p
        join r.tags t
        where r.delYn = com.example.auction.common.domain.DelYN.N
          and r.createdAt >= :start and r.createdAt < :end
          and p.delYn = com.example.auction.common.domain.DelYN.N
          and (p.blocked = false or p.blocked is null)
          and r.seller.delYn = com.example.auction.common.domain.DelYN.N
          and t = :tag
        group by r.seller.userId
    """)
    List<SellerCountRow> countMonthlyTagBySeller(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("tag") ReviewTag tag
    );
}
