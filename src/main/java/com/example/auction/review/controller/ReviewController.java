package com.example.auction.review.controller;

import com.example.auction.review.dto.ReviewCreateDto;
import com.example.auction.review.dto.ReviewDetailDto;
import com.example.auction.review.dto.ReviewListDto;
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

    // 상품별 리뷰
    @GetMapping("/product/{productId}")
    public Page<ReviewListDto> listByProduct(@PathVariable Long productId, Pageable pageable) {
        return reviewService.listByProduct(productId, pageable);
    }
    // 판매자(유저)별 리뷰
    @GetMapping("/seller/{sellerId}")
    public Page<ReviewListDto> listBySeller(@PathVariable Long sellerId, Pageable pageable) {
        return reviewService.listBySeller(sellerId, pageable);
    }
}
