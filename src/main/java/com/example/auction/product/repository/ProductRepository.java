package com.example.auction.product.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.example.auction.product.domain.SellYN;
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
    List<Product> findBySellYNAndCreatedAtBeforeOrderByCreatedAtAsc(SellYN sellYN, LocalDateTime createdBefore);

	Optional<Product> findByProductIdAndDelYnAndBlocked(Long productId, DelYN delYn, Boolean blocked);
	List<Product> findByBlockedAndDelYn(Boolean blocked, DelYN delYn);


	// 최근 24시간 30분 내외 생성 경매 확인
	List<Product> findBySellYNAndDelYnAndBlockedAndCreatedAtAfter(
			SellYN sellYN, DelYN delYn, Boolean blocked, LocalDateTime createdAtAfter
	);
	List<Product> findBySellYNAndDelYnAndBlocked(SellYN sellYN, DelYN delYn, Boolean blocked);

//    List<Product> findAllByUser_Email(String email);

    @Query("SELECT new com.example.auction.product.dto.ProductListDto(" +
            "p.productId, " +
            "p.productName, " +
            "p.productContent, " +
            "p.price, " +
            "p.sellYN, " +
            "(SELECT img.url FROM ProductImage img " +
            " WHERE img.product.productId = p.productId " +
            " ORDER BY img.position ASC " +
            " LIMIT 1), " +
            "p.category.categoryId, " +
            "p.user.email, " +
            "(SELECT b.bidAmount FROM Bid b " +
            " WHERE b.product.productId = p.productId " +
            " ORDER BY b.createdAt DESC " +
            " LIMIT 1)) " +
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
            "p.sellYN, " +
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
            "GROUP BY p.productId, p.productName, p.productContent, p.price, p.sellYN " +
            "ORDER BY p.createdAt DESC")
    List<ProductWithBidDto> findAllByUserEmailWithBidInfo(@Param("email") String email);

    @Query("SELECT new com.example.auction.product.dto.ProductWithBidDto(" +
            "p.productId, " +
            "p.productName, " +
            "p.productContent, " +
            "p.price, " +
            "p.sellYN, " +
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
            "GROUP BY p.productId, p.productName, p.productContent, p.price, p.sellYN " +
            "ORDER BY p.createdAt DESC")
    List<ProductWithBidDto> findWishlistByUserEmailWithBidInfo(@Param("email") String email);
}
