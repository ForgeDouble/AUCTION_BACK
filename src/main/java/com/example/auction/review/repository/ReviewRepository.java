package com.example.auction.review.repository;

import com.example.auction.common.domain.DelYN;
import com.example.auction.review.domain.Review;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByProduct_ProductIdAndReviewer_UserIdAndDelYn(Long productId, Long reviewerId, DelYN delYn);
}
