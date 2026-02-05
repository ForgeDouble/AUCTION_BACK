package com.example.auction.review.repository;

import com.example.auction.common.domain.DelYN;
import com.example.auction.review.domain.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByProduct_ProductIdAndReviewer_UserIdAndDelYn(Long productId, Long reviewerId, DelYN delYn);
    Page<Review> findAllBySeller_UserIdAndDelYnOrderByCreatedAtDesc(Long sellerId, DelYN delYn, Pageable pageable);
    Page<Review> findAllByProduct_ProductIdAndDelYnOrderByCreatedAtDesc(Long productId, DelYN delYn, Pageable pageable);
    Page<Review> findAllByReviewer_UserIdAndDelYnOrderByCreatedAtDesc(Long reviewerId, DelYN delYn, Pageable pageable);
}
