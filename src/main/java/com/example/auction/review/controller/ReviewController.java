package com.example.auction.review.controller;

import com.example.auction.review.dto.*;
import com.example.auction.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/review")
public class ReviewController {
    private final ReviewService reviewService;

    // 리뷰 생성
    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReviewDetailDto create(
            @RequestPart("req") ReviewCreateDto dto,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        return reviewService.create(dto, files);
    }
    @GetMapping("/can-write/{productId}")
    public CanWriteReviewDto canWrite(@PathVariable Long productId) {
        return reviewService.canWrite(productId);
    }
    // 상품별 리뷰 조회
    @GetMapping("/product/{productId}")
    public Page<ReviewListDto> listByProduct(@PathVariable Long productId, Pageable pageable) {
        return reviewService.listByProduct(productId, pageable);
    }
    // 판매자(유저)별 리뷰 조회
    @GetMapping("/seller/{sellerId}")
    public Page<ReviewListDto> listBySeller(@PathVariable Long sellerId, Pageable pageable) {
        return reviewService.listBySeller(sellerId, pageable);
    }
    // 판매자 요약(평균/카운트/태그카운트)
    @GetMapping("/seller/{sellerId}/summary")
    public ReviewSellerSummaryDto sellerSummary(@PathVariable Long sellerId) {
        return reviewService.sellerSummary(sellerId);
    }
    // 내가 쓴 리뷰 조회
    @GetMapping("/me")
    public Page<ReviewListDto> myReviews(Pageable pageable) {
        return reviewService.myReviews(pageable);
    }
}
