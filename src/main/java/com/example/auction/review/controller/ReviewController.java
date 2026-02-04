package com.example.auction.review.controller;

import com.example.auction.review.dto.ReviewCreateDto;
import com.example.auction.review.dto.ReviewDetailDto;
import com.example.auction.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
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
}
