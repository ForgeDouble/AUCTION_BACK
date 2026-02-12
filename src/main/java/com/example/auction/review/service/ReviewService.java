package com.example.auction.review.service;

import com.example.auction.bid.domain.Bid;
import com.example.auction.bid.domain.IsWinned;
import com.example.auction.bid.repository.BidRepository;
import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.BadRequestException;
import com.example.auction.common.exception.InternalErrorException;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.common.exception.UnauthorizedAccessException;
import com.example.auction.product.domain.Product;
import com.example.auction.product.domain.Status;
import com.example.auction.product.repository.ProductRepository;
import com.example.auction.review.domain.Review;
import com.example.auction.review.domain.ReviewImage;
import com.example.auction.review.domain.ReviewTag;
import com.example.auction.review.dto.*;
import com.example.auction.review.repository.ReviewRepository;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
@Slf4j
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
                .orElseThrow(() -> {
                            log.warn("[INVALID_USER] 존재하지 않거나 유효하지 않은 유저 email={}", email);
                            throw new UnauthorizedAccessException("INVALID_USER", "유효하지 않은 유저입니다.");
                        }
                );
    }

    // 임시제한 여부 확인
    /* 계정 권한 자체의 무넺라 UnauthorizedAccessException */
    private void ensureUserCanWrite(User user) {
        if (Boolean.TRUE.equals(user.getViewOnly())) {
            throw new UnauthorizedAccessException("ACCOUNT_WARNING_STATE", "임시 제한(view-only) 상태라 리뷰를 작성할 수 없습니다.");
        }
        if (user.getSuspendedUntil() != null && LocalDateTime.now().isBefore(user.getSuspendedUntil())) {
            throw new UnauthorizedAccessException("ACCOUNT_SUSPENDED", "정지된 계정은 리뷰를 작성할 수 없습니다.");
        }
    }

    // 별 정책 (0.5 단위, 0.0~5.0)
    private BigDecimal parseRatingHalf(Double rating) {
        if (rating == null) {
            throw new BadRequestException("RATING_REQUIRED", "만족도는 필수입니다.");
        }

        BigDecimal r = BigDecimal.valueOf(rating);
        if (r.compareTo(BigDecimal.ZERO) < 0 || r.compareTo(BigDecimal.valueOf(5)) > 0) {
            throw new BadRequestException("RATING_OUT_OF_RANGE", "만족도는 0.0 ~ 5.0 사이여야 합니다.");
        }
        try {
            r.multiply(BigDecimal.valueOf(2)).intValueExact();
        } catch (ArithmeticException ex) {
            throw new BadRequestException("RATING_STEP_INVALID", "만족도는 0.5점 단위로 입력해야 합니다.");
        }
        return r.setScale(1);
    }

    // 태그 정책
    private void validateTags(List<ReviewTag> tags) {
        if (tags == null || tags.isEmpty()) {
            throw new BadRequestException("TAG_REQUIRED", "리뷰 태그는 최소 1개 이상 선택해야 합니다.");
        }
//        if (tags.size() > 10) {
//            throw new BadRequestException("BAD_REQUEST", "리뷰 태그 선택 개수가 너무 많습니다.");
//        }
    }

    // 리뷰 생성
    @Transactional
    public ReviewDetailDto create(ReviewCreateDto reviewCreateDto, List<MultipartFile> files) {
        User reviewer = me();
        ensureUserCanWrite(reviewer);

        if (reviewCreateDto == null || reviewCreateDto.getProductId() == null) {
            throw new BadRequestException("PRODUCT_ID_REQUIRED", "productId가 필요합니다.");
        }

        Product product = productRepository.findByProductIdAndDelYnAndBlocked(reviewCreateDto.getProductId(), DelYN.N, false)
                .orElseThrow(() -> new ResourceNotFoundException("PRODUCT_NOT_FOUND", "존재하지 않거나 차단된 상품입니다."));

        if (product.getStatus() != Status.SELLED) {
            throw new BadRequestException("PRODUCT_BE_SELLED", "판매 완료된 상품만 리뷰를 작성할 수 있습니다.");
        }

        // 판매자
        User seller = product.getUser();
//        if (seller == null) {
//            throw new ResourceNotFoundException("SELLER_NOT_FOUND", "판매자 정보를 찾을 수 없습니다.");
//        }

        if (Objects.equals(seller.getUserId(), reviewer.getUserId())) {
            throw new BadRequestException("SELF_REVIEW_FORBIDDEN", "본인 상품에는 리뷰를 작성할 수 없습니다.");
        }

        // 낙찰자 검증
        Bid winnerBid = bidRepository.findByProduct_ProductIdAndIsWinned(product.getProductId(), IsWinned.Y)
                .orElseThrow(() -> new BadRequestException("WINNER_BID_NOT_FOUND", "낙찰자만 리뷰를 작성할 수 있습니다."));

        if (!Objects.equals(winnerBid.getUser().getUserId(), reviewer.getUserId())) {
            throw new UnauthorizedAccessException("REVIEWER_NOT_WINNER", "낙찰자만 리뷰를 작성할 수 있습니다.");
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

        BigDecimal rating = parseRatingHalf(reviewCreateDto.getRating());
        validateTags(reviewCreateDto.getTags());

        String content = null;
        if (reviewCreateDto.getContent() != null && !reviewCreateDto.getContent().isBlank()) {
            content = reviewCreateDto.getContent().trim();
        }

        Review review = Review.builder()
                .product(product)
                .seller(seller)
                .reviewer(reviewer)
                .rating(rating)
                .content(content)
                .tags(new ArrayList<>(reviewCreateDto.getTags()))
                .build();

        Review saved = reviewRepository.save(review);

        List<ReviewImage> imgs = reviewImageService.uploadAll(saved, files);
        List<ReviewImageDto> imageDtos = imgs.stream().map(ReviewImageDto::from).toList();

        return ReviewDetailDto.from(saved, imageDtos);
    }

    // 작성 가능 여부
    @Transactional(readOnly = true)
    public CanWriteReviewDto canWrite(Long productId) {
        User reviewer = me();

        if (productId == null) {
            return CanWriteReviewDto.builder().canWrite(false).reason("productId가 필요합니다.").build();
        }

        Optional<Product> opt = productRepository.findByProductIdAndDelYnAndBlocked(productId, DelYN.N, false);
        if (opt.isEmpty()) {
            return CanWriteReviewDto.builder().canWrite(false).reason("존재하지 않거나 차단된 상품입니다.").build();
        }

        Product product = opt.get();
        if (product.getStatus() != Status.SELLED) {
            return CanWriteReviewDto.builder().canWrite(false).reason("판매 완료 상태가 아닙니다.").build();
        }

        if (product.getUser() != null && Objects.equals(product.getUser().getUserId(), reviewer.getUserId())) {
            return CanWriteReviewDto.builder().canWrite(false).reason("본인 상품입니다.").build();
        }

        boolean dup = reviewRepository.existsByProduct_ProductIdAndReviewer_UserIdAndDelYn(productId, reviewer.getUserId(), DelYN.N);
        if (dup) {
            return CanWriteReviewDto.builder().canWrite(false).reason("이미 리뷰를 작성했습니다.").build();
        }

        Optional<Bid> winner = bidRepository.findByProduct_ProductIdAndIsWinned(productId, IsWinned.Y);
        if (winner.isEmpty() || winner.get().getUser() == null) {
            return CanWriteReviewDto.builder().canWrite(false).reason("낙찰자 정보가 없습니다.").build();
        }

        if (!Objects.equals(winner.get().getUser().getUserId(), reviewer.getUserId())) {
            return CanWriteReviewDto.builder().canWrite(false).reason("낙찰자만 작성 가능합니다.").build();
        }

        return CanWriteReviewDto.builder().canWrite(true).reason("OK").build();
    }

    // 판매자 요약
    @Transactional(readOnly = true)
    public ReviewSellerSummaryDto sellerSummary(Long sellerId) {
        Page<Review> page = reviewRepository.findAllBySeller_UserIdAndDelYnOrderByCreatedAtDesc(sellerId, DelYN.N, Pageable.unpaged());
        List<Review> reviews = page.getContent();

        long count = reviews.size();

        Map<ReviewTag, Long> tagCounts = new EnumMap<>(ReviewTag.class);
        for (ReviewTag t : ReviewTag.values()) tagCounts.put(t, 0L);

        double sum = 0.0;
        for (Review review : reviews) {
            sum += (review.getRating() == null ? 0.0 : review.getRating().doubleValue());
            if (review.getTags() != null) {
                for (ReviewTag rt : review.getTags()) {
                    tagCounts.put(rt, tagCounts.getOrDefault(rt, 0L) + 1);
                }
            }
        }

        double avg = (count > 0) ? (sum / (double) count) : 0.0;

        return ReviewSellerSummaryDto.builder()
                .sellerId(sellerId)
                .reviewCount(count)
                .avgRating(avg)
                .tagCounts(tagCounts)
                .build();
    }

    // 상품별 리뷰 목록
    @Transactional(readOnly = true)
    public Page<ReviewListDto> listByProduct(Long productId, Pageable pageable) {
        Page<Review> page = reviewRepository.findAllByProduct_ProductIdAndDelYnOrderByCreatedAtDesc(productId, DelYN.N, pageable);
        return page.map(this::toListDto);
    }

    // 판매자 받은 리뷰 목록
    @Transactional(readOnly = true)
    public Page<ReviewListDto> listBySeller(Long sellerId, Pageable pageable) {
        Page<Review> page = reviewRepository.findAllBySeller_UserIdAndDelYnOrderByCreatedAtDesc(sellerId, DelYN.N, pageable);
        return page.map(this::toListDto);
    }

    // 내가 작성한 리뷰 목록
    @Transactional(readOnly = true)
    public Page<ReviewListDto> myReviews(Pageable pageable) {
        User reviewer = me();
        Page<Review> page = reviewRepository.findAllByReviewer_UserIdAndDelYnOrderByCreatedAtDesc(reviewer.getUserId(), DelYN.N, pageable);
        return page.map(this::toListDto);
    }

    // 안 쓴 리뷰 목록 (낙찰자 기준, SELLED, 미작성)
    @Transactional(readOnly = true)
    public Page<PendingReviewRowDto> myPendingReviews(Pageable pageable) {
        User reviewer = me();
        Page<Bid> bids = bidRepository.findPendingReviewBids(reviewer.getUserId(), pageable);

        return bids.map(b -> {
            Product product = b.getProduct();
            User seller = (product != null ? product.getUser() : null);

            return PendingReviewRowDto.builder()
                    .productId(product.getProductId())
                    .productName(product.getProductName())
                    .sellerId(seller == null ? null : seller.getUserId())
                    .sellerNick(seller == null ? null : seller.getNickname())
                    .winnerBidAmount(b.getBidAmount())
                    .auctionEndTime(product.getAuctionEndTime())
                    .build();
        });
    }

    // 리뷰 상세 (이미지 전체)
    @Transactional(readOnly = true)
    public ReviewDetailDto detail(Long reviewId) {
        if (reviewId == null) throw new BadRequestException("REVIEW_ID_REQUIRED", "reviewId가 필요합니다.");

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("REVIEW_NOT_FOUND", "리뷰를 찾을 수 없습니다."));

        List<ReviewImage> imgs = reviewImageService.findByReviewId(reviewId);
        List<ReviewImageDto> imageDtos = (imgs == null ? List.of() : imgs.stream().map(ReviewImageDto::from).toList());

        return ReviewDetailDto.from(review, imageDtos);
    }

    private ReviewListDto toListDto(Review review) {
        String firstImageUrl = null;
        List<ReviewImage> imgs = reviewImageService.findByReviewId(review.getReviewId());
        if (imgs != null && !imgs.isEmpty()) firstImageUrl = imgs.get(0).getUrl();

        return ReviewListDto.builder()
                .reviewId(review.getReviewId())
                .productId(review.getProduct().getProductId())
                .productName(review.getProduct().getProductName())
                .reviewerId(review.getReviewer().getUserId())
                .reviewerNick(review.getReviewer().getNickname())
                .reviewerProfileImageUrl(review.getReviewer().getProfileImageUrl())
                .rating(review.getRating() == null ? 0.0 : review.getRating().doubleValue())
                .tags(review.getTags() == null ? List.of() : new ArrayList<>(review.getTags()))
                .content(review.getContent())
                .firstImageUrl(firstImageUrl)
                .createdAt(review.getCreatedAt())
                .build();
    }

}