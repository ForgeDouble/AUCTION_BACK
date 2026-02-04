package com.example.auction.review.service;

import com.example.auction.common.service.S3ObjectService;
import com.example.auction.common.util.FileValidationUtil;
import com.example.auction.common.util.S3KeyUtil;
import com.example.auction.review.domain.Review;
import com.example.auction.review.domain.ReviewImage;
import com.example.auction.review.repository.ReviewImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
public class ReviewImageService {
    private final ReviewImageRepository reviewImageRepository;
    private final FileValidationUtil fileValidationUtil;
    private final S3KeyUtil s3KeyUtil;
    private final S3ObjectService s3ObjectService;

    public ReviewImageService(ReviewImageRepository reviewImageRepository, FileValidationUtil fileValidationUtil, S3KeyUtil s3KeyUtil, S3ObjectService s3ObjectService) {
        this.reviewImageRepository = reviewImageRepository;
        this.fileValidationUtil = fileValidationUtil;
        this.s3KeyUtil = s3KeyUtil;
        this.s3ObjectService = s3ObjectService;
    }


    @Transactional
    public List<ReviewImage> uploadAll(Review review, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        List<ReviewImage> saved = new ArrayList<>();
        int pos = 1;

        for (MultipartFile multipartFile : files) {
            fileValidationUtil.ensureImage(multipartFile);

            String ext = fileValidationUtil.ext(multipartFile.getContentType(), multipartFile.getOriginalFilename());
            String key = s3KeyUtil.reviewImageKey(review.getReviewId(), ext);

            String storedKey = s3ObjectService.put(key, multipartFile);
            String url = s3ObjectService.toPublicUrl(storedKey);

            ReviewImage img = ReviewImage.builder()
                    .review(review)
                    .s3Key(storedKey)
                    .url(url)
                    .position(pos++)
                    .build();

            saved.add(reviewImageRepository.save(img));
        }
        return saved;
    }


}
