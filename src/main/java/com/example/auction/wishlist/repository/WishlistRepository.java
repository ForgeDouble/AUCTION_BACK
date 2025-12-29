package com.example.auction.wishlist.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.auction.wishlist.domain.Wishlist;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {
    Optional<Wishlist> findByUser_UserId(Long userId);

    // 사용자 중복 제어
    boolean existsByUser_UserIdAndProduct_ProductId(Long userId, Long productId);
    Optional<Wishlist> findByWishlistId(Long wishlistId);

    /* userId 와 productId를 통해 whishlistId를 조회 */
    @Query("SELECT w.wishlistId FROM Wishlist w WHERE w.user.userId = :userId AND w.product.productId = :productId")
    Optional<Long> findWishlistIdByUser_UserIdAndProduct_ProductId(
            @Param("userId") Long userId,
            @Param("productId") Long productId
    );
}
