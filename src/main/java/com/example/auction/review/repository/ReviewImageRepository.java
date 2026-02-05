package com.example.auction.review.repository;

import com.example.auction.common.domain.DelYN;
import com.example.auction.review.domain.ReviewImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewImageRepository extends JpaRepository<ReviewImage, Long> {
    List<ReviewImage> findAllByReview_ReviewIdOrderByPositionAsc(Long reviewId);
}