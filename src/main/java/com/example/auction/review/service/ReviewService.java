package com.example.auction.review.service;

import com.example.auction.bid.domain.Bid;
import com.example.auction.bid.domain.IsWinned;
import com.example.auction.bid.repository.BidRepository;
import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.BadRequestException;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.common.exception.UnauthorizedAccessException;
import com.example.auction.product.domain.Product;
import com.example.auction.product.domain.Status;
import com.example.auction.product.repository.ProductRepository;
import com.example.auction.review.domain.Review;
import com.example.auction.review.domain.ReviewImage;
import com.example.auction.review.domain.ReviewTag;
import com.example.auction.review.dto.ReviewCreateDto;
import com.example.auction.review.dto.ReviewDetailDto;
import com.example.auction.review.dto.ReviewImageDto;
import com.example.auction.review.repository.ReviewRepository;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final BidRepository bidRepository;
    private final UserRepository userRepository;
    private final ReviewImageService reviewImageService;

    public ReviewService(ReviewRepository reviewRepository, ProductRepository productRepository, BidRepository bidRepository, UserRepository userRepository, ReviewImageService reviewImageService) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.bidRepository = bidRepository;
        this.userRepository = userRepository;
        this.reviewImageService = reviewImageService;
    }

    // 유저 여부 확인
    private User me() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "로그인 유저를 찾을 수 없습니다."));
    }
    // 임시제한 여부 확인
    private void ensureUserCanWrite(User user) {
        if (Boolean.TRUE.equals(user.getViewOnly())) {
            throw new UnauthorizedAccessException("NOT_ALLOWED", "임시 제한(view-only) 상태라 리뷰를 작성할 수 없습니다.");
        }
        if (user.getSuspendedUntil() != null && LocalDateTime.now().isBefore(user.getSuspendedUntil())) {
            throw new UnauthorizedAccessException("NOT_ALLOWED", "정지된 계정은 리뷰를 작성할 수 없습니다.");
        }
    }

    // 별 정책 
    private int parseRatingHalf(Double rating) {
        if (rating == null) {
            throw new BadRequestException("BAD_REQUEST", "만족도(rating)는 필수입니다.");
        }

        BigDecimal r = BigDecimal.valueOf(rating);
        if (r.compareTo(BigDecimal.ZERO) < 0 || r.compareTo(BigDecimal.valueOf(5)) > 0) {
            throw new BadRequestException("BAD_REQUEST", "만족도는 0.0 ~ 5.0 사이여야 합니다.");
        }

        try {
            int half = r.multiply(BigDecimal.valueOf(2)).intValueExact(); 
            if (half < 0 || half > 10) {
                throw new BadRequestException("BAD_REQUEST", "만족도 범위가 올바르지 않습니다.");
            }
            return half;
        } catch (ArithmeticException ex) {
            throw new BadRequestException("BAD_REQUEST", "만족도는 0.5점 단위로 입력해야 합니다.");
        }
    }

    // 태그 정책
    private void validateTags(List<ReviewTag> tags) {
        if (tags == null || tags.isEmpty()) {
            throw new BadRequestException("BAD_REQUEST", "리뷰 태그는 최소 1개 이상 선택해야 합니다.");
        }
        if (tags.size() > 10) {
            throw new BadRequestException("BAD_REQUEST", "리뷰 태그 선택 개수가 너무 많습니다.");
        }
    }

    // 리뷰 생성
    @Transactional
    public ReviewDetailDto create(ReviewCreateDto req, List<MultipartFile> files) {
        User reviewer = me();
        ensureUserCanWrite(reviewer);

        if (req == null || req.getProductId() == null) {
            throw new BadRequestException("BAD_REQUEST", "productId가 필요합니다.");
        }

        Product product = productRepository.findByProductIdAndDelYnAndBlocked(req.getProductId(), DelYN.N, false)
                .orElseThrow(() -> new ResourceNotFoundException("PRODUCT_NOT_FOUND", "존재하지 않거나 차단된 상품입니다."));

        if (product.getStatus() != Status.SELLED) {
            throw new UnauthorizedAccessException("NOT_ALLOWED", "판매 완료된 상품만 리뷰를 작성할 수 있습니다.");
        }

        // 판매자
        User seller = product.getUser();
        if (seller == null) {
            throw new ResourceNotFoundException("SELLER_NOT_FOUND", "판매자 정보를 찾을 수 없습니다.");
        }

        if (Objects.equals(seller.getUserId(), reviewer.getUserId())) {
            throw new UnauthorizedAccessException("NOT_ALLOWED", "본인 상품에는 리뷰를 작성할 수 없습니다.");
        }

        // 낙찰자 검증 -> bid에서 isWinned = Y 인 유저가 나여야 함
        Bid winnerBid = bidRepository.findByProduct_ProductIdAndIsWinned(product.getProductId(), IsWinned.Y)
                .orElseThrow(() -> new UnauthorizedAccessException("NOT_ALLOWED", "낙찰자만 리뷰를 작성할 수 있습니다."));

        if (!Objects.equals(winnerBid.getUser().getUserId(), reviewer.getUserId())) {
            throw new UnauthorizedAccessException("NOT_ALLOWED", "낙찰자만 리뷰를 작성할 수 있습니다.");
        }

        // 리뷰 중복 방지
        boolean exists = reviewRepository.existsByProduct_ProductIdAndReviewer_UserIdAndDelYn(
                product.getProductId(),
                reviewer.getUserId(),
                DelYN.N
        );
        if (exists) {
            throw new BadRequestException("DUPLICATE_REVIEW", "이미 해당 상품에 대한 리뷰를 작성했습니다.");
        }

        int ratingHalf = parseRatingHalf(req.getRating());
        validateTags(req.getTags());

        String content = null;
        if (req.getContent() != null && !req.getContent().isBlank()) {
            content = req.getContent().trim();
        }

        Review review = Review.builder()
                .product(product)
                .seller(seller)
                .reviewer(reviewer)
                .ratingHalf(ratingHalf)
                .content(content)
                .tags(new ArrayList<>(req.getTags()))
                .build();

        Review saved = reviewRepository.save(review);

        List<ReviewImage> imgs = reviewImageService.uploadAll(saved, files);
        List<ReviewImageDto> imageDtos = imgs.stream().map(ReviewImageDto::from).toList();

        return ReviewDetailDto.from(saved, imageDtos);
    }
}
