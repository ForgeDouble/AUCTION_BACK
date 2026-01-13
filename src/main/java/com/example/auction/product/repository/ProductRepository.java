package com.example.auction.product.repository;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import com.example.auction.product.domain.Status;
import com.example.auction.product.dto.ProductListDto;
import com.example.auction.product.dto.ProductWithBidDto;
import com.example.auction.product.dto.Top3ProductDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.auction.common.domain.DelYN;
import com.example.auction.product.domain.Product;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface ProductRepository extends JpaRepository<Product, Long> {
	Optional<Product> findByProductIdAndDelYn(Long productId, DelYN delYN);
    List<Product> findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(Status status, LocalDateTime createdBefore);

	Optional<Product> findByProductIdAndDelYnAndBlocked(Long productId, DelYN delYn, Boolean blocked);
	List<Product> findByBlockedAndDelYn(Boolean blocked, DelYN delYn);


	// 최근 24시간 30분 내외 생성 경매 확인
	List<Product> findByStatusAndDelYnAndBlockedAndCreatedAtAfter(
			Status status, DelYN delYn, Boolean blocked, LocalDateTime createdAtAfter
	);
	List<Product> findByStatusAndDelYnAndBlocked(Status status, DelYN delYn, Boolean blocked);

//    List<Product> findAllByUser_Email(String email);

    @Query("SELECT new com.example.auction.product.dto.ProductListDto(" +
            "p.productId, " +
            "p.productName, " +
            "p.productContent, " +
            "p.price, " +
            "p.status, " +
            "(SELECT img.url FROM ProductImage img " +
            " WHERE img.product.productId = p.productId " +
            " ORDER BY img.position ASC " +
            " LIMIT 1), " +
            "p.category.categoryId, " +
            "p.user.email, " +
            "COALESCE(MAX(b.bidAmount), 0), " +
            "COUNT(DISTINCT b.bidId), " +
            "p.createdAt) " +
            "FROM Product p " +
            "LEFT JOIN Bid b ON b.product.productId = p.productId " +
            "WHERE p.delYn = com.example.auction.common.domain.DelYN.N " +
            "  AND (p.blocked = false OR p.blocked IS NULL) " +
            "  AND (:categoryIds IS NULL OR p.category.categoryId IN :categoryIds) " +
            "  AND (:search IS NULL OR :search = '' OR LOWER(p.productName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "  AND (:minPrice IS NULL OR p.price >= :minPrice) " +
            "  AND (:maxPrice IS NULL OR p.price <= :maxPrice) " +
            "  AND (:statuses IS NULL OR p.status IN :statuses) " +
            "GROUP BY p.productId, p.productName, p.productContent, p.price, p.status, " +
            "         p.category.categoryId, p.user.email, p.createdAt " +
            "ORDER BY " +
            "CASE WHEN :sortBy = 'ENDING_SOON' THEN " +
            "  CASE WHEN p.status = com.example.auction.product.domain.Status.PROCESSING THEN 0 ELSE 1 END " +
            "END ASC, " +
            "CASE WHEN :sortBy = 'ENDING_SOON' THEN p.createdAt END ASC, " +
            "CASE WHEN :sortBy = 'MOST_BIDS' THEN COUNT(DISTINCT b.bidId) END DESC, " +
            "CASE WHEN :sortBy = 'PRICE_ASC' THEN MAX(b.bidAmount) END ASC, " +
            "CASE WHEN :sortBy = 'PRICE_DESC' THEN MAX(b.bidAmount) END DESC, " +
            "CASE WHEN :sortBy = 'NEWEST' THEN p.createdAt END DESC")
    Page<ProductListDto> findActiveProducts(
            @Param("categoryIds") List<Long> categoryIds,
            @Param("search") String search,
            @Param("minPrice") Long minPrice,
            @Param("maxPrice") Long maxPrice,
            @Param("statuses") List<Status> statuses,
            @Param("sortBy") String sortBy,
            Pageable pageable
    );

    @Query("SELECT new com.example.auction.product.dto.ProductWithBidDto(" +
            "p.productId, " +
            "p.productName, " +
            "p.productContent, " +
            "p.price, " +
            "p.status, " +
            "COUNT(b.bidId), " +
            "COALESCE(MAX(b.bidAmount), 0), " +
            "(SELECT img.url FROM ProductImage img " +
            " WHERE img.product.productId = p.productId " +
            " ORDER BY img.position ASC " +
            " LIMIT 1)) " +
            "FROM Product p " +
            "LEFT JOIN Bid b ON b.product = p " +
            "WHERE p.user.email = :email " +
            "  AND p.delYn = com.example.auction.common.domain.DelYN.N " +
            "  AND (p.blocked = false OR p.blocked IS NULL) " +
            "GROUP BY p.productId, p.productName, p.productContent, p.price, p.status " +
            "ORDER BY p.createdAt DESC")
    Page<ProductWithBidDto> findAllByUserEmailWithBidInfo(@Param("email") String email, Pageable pageable);

    @Query("SELECT new com.example.auction.product.dto.ProductWithBidDto(" +
            "p.productId, " +
            "p.productName, " +
            "p.productContent, " +
            "p.price, " +
            "p.status, " +
            "COUNT(b.bidId), " +
            "COALESCE(MAX(b.bidAmount), 0), " +
            "(SELECT img.url FROM ProductImage img " +
            " WHERE img.product.productId = p.productId " +
            " ORDER BY img.position ASC " +
            " LIMIT 1)) " +
            "FROM Product p " +
            "INNER JOIN Wishlist w ON w.product.productId = p.productId " +
            "LEFT JOIN Bid b ON b.product = p " +
            "WHERE w.user.email = :email " +
            "  AND p.delYn = com.example.auction.common.domain.DelYN.N " +
            "  AND (p.blocked = false OR p.blocked IS NULL) " +
            "GROUP BY p.productId, p.productName, p.productContent, p.price, p.status " +
            "ORDER BY p.createdAt DESC")
    Page<ProductWithBidDto> findWishlistByUserEmailWithBidInfo(@Param("email") String email, Pageable pageable);

    @Query("SELECT new com.example.auction.product.dto.Top3ProductDto(" +
            "p.productId, " +
            "p.productName, " +
            "p.status, " +
            "COUNT(b.bidId), " +
            "COALESCE(MAX(b.bidAmount), 0), " +
            "(SELECT img.url FROM ProductImage img " +
            " WHERE img.product.productId = p.productId " +
            " ORDER BY img.position ASC " +
            " LIMIT 1), " +
            "p.createdAt) " +  // 추가
            "FROM Product p " +
            "LEFT JOIN Bid b ON b.product = p " +
            "WHERE p.delYn = com.example.auction.common.domain.DelYN.N " +
            "  AND (p.blocked = false OR p.blocked IS NULL) " +
            "  AND p.status = com.example.auction.product.domain.Status.PROCESSING " +
            "GROUP BY p.productId, p.productName, p.status, p.createdAt " +  // createdAt 추가
            "ORDER BY COUNT(b.bidId) DESC " +
            "LIMIT 3")
    List<Top3ProductDto> findTop3ByBidCount();


    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // 진행중인 경매수 (차단 제외)
    long countByStatusAndBlockedFalse(Status status);

    // 차단된 상품 수
    long countByBlockedTrue();

    // 금일 판매된 경매수
    long countByStatusAndUpdatedAtBetween(Status status, LocalDateTime start, LocalDateTime end);

    // 금일 종료되었지만 미판매(NOTSELLED)된 경매수

    // 관리자에서 전체 경매 수
    long countByStatus(Status status);

    // READY/PROCESSING/SELLED/NOTSELLED 분포 확인
    @Query("""
        select count(p)
        from Product p
        where p.status = :status
          and p.blocked = false
    """)
    long countByStatusExcludingBlocked(@Param("status") Status status);


    @Query("""
        select p.category.categoryId as categoryId, count(p) as cnt
        from Product p
        where p.delYn = com.example.auction.common.domain.DelYN.N
        and (p.blocked = false or p.blocked is null)
        and p.category is not null
        group by p.category.categoryId
    """)
    List<CategoryCountRow> countByCategoryIdExcludingDeletedBlocked();

    //관리자 화면 - 최근 N개 조회
    @Query("""
    select p
    from Product p
    left join fetch p.user
    left join fetch p.category
    where p.delYn = com.example.auction.common.domain.DelYN.N
    order by p.createdAt desc
""")
    Page<Product> findAdminMonitoring(Pageable pageable);

    // 최근 7일간 생성/종료 경매 확인 repository
    interface DayCountRow {
        Date getD();
        Long getCnt();
    }

    @Query("""
        select function('date', p.createdAt) as d, count(p) as cnt
        from Product p
        where p.delYn = com.example.auction.common.domain.DelYN.N
          and (p.blocked = false or p.blocked is null)
          and p.createdAt >= :from and p.createdAt < :to
        group by function('date', p.createdAt)
        order by function('date', p.createdAt)
    """)
    List<DayCountRow> countCreatedDaily(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
        select function('date', p.updatedAt) as d, count(p) as cnt
        from Product p
        where p.delYn = com.example.auction.common.domain.DelYN.N
          and (p.blocked = false or p.blocked is null)
          and p.status in :statuses
          and p.updatedAt >= :from and p.updatedAt < :to
        group by function('date', p.updatedAt)
        order by function('date', p.updatedAt)
    """)
    List<DayCountRow> countEndedDaily(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("statuses") List<Status> statuses
    );


    // 월별 경매 추이 repository
    interface MonthlyAmountRow {
        String getYm();      // yyyy-MM
        Long getAmount();    // sum
    }

    @Query(value = """
        select
            date_format(p.updated_at, '%Y-%m') as ym,
            sum(coalesce(mx.max_bid, p.price, 0)) as amount
        from product p
        left join (
            select b.product_id as product_id, max(b.bid_amount) as max_bid
            from bid b
            group by b.product_id
        ) mx on mx.product_id = p.product_id
        where p.del_yn = 'N'
          and (p.blocked = 0 or p.blocked is null)
          and p.status = 'SELLED'
          and p.updated_at >= :from and p.updated_at < :to
        group by date_format(p.updated_at, '%Y-%m')
        order by ym
    """, nativeQuery = true)
    List<MonthlyAmountRow> sumMonthlyTradeAmountSold(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
