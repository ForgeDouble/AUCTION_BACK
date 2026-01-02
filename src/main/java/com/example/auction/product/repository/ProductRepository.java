package com.example.auction.product.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.example.auction.product.domain.Status;
import com.example.auction.product.dto.ProductListDto;
import com.example.auction.product.dto.ProductWithBidDto;
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
            "(SELECT b.bidAmount FROM Bid b " +
            " WHERE b.product.productId = p.productId " +
            " ORDER BY b.createdAt DESC " +
            " LIMIT 1), " +
            "(SELECT COUNT(b) FROM Bid b " +
            " WHERE b.product.productId = p.productId)) " +
            "FROM Product p " +
            "WHERE p.delYn = com.example.auction.common.domain.DelYN.N " +
            "  AND (p.blocked = false OR p.blocked IS NULL) " +
            "  AND (:categoryIds IS NULL OR p.category.categoryId IN :categoryIds) " +
            "  AND (:search IS NULL OR :search = '' OR LOWER(p.productName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "  AND (:minPrice IS NULL OR p.price >= :minPrice) " +
            "  AND (:maxPrice IS NULL OR p.price <= :maxPrice)")
    Page<ProductListDto> findActiveProducts(
            @Param("categoryIds") List<Long> categoryIds,
            @Param("search") String search,
            @Param("minPrice") Long minPrice,
            @Param("maxPrice") Long maxPrice,
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
    List<ProductWithBidDto> findAllByUserEmailWithBidInfo(@Param("email") String email);

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
    List<ProductWithBidDto> findWishlistByUserEmailWithBidInfo(@Param("email") String email);


    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // 진행중인 경매수 (차단 제외)
    long countByStatusAndBlockedFalse(Status status);

    // 차단된 상품 수
    long countByBlockedTrue();

    // 금일 판매된 경매수 (status가 SELLED로 바뀐 시점을 updatedAt으로 본다)
    long countByStatusAndUpdatedAtBetween(Status status, LocalDateTime start, LocalDateTime end);

    // 금일 종료되었지만 미판매(NOTSELLED)된 경매수

    // 관리자에서 전체 경매 수
    long countByStatus(Status status);

    // 참고: READY/PROCESSING/SELLED/NOTSELLED 분포를 한방에 보고 싶으면
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
}
