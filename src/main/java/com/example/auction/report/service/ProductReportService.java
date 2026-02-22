package com.example.auction.report.service;

import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.InternalErrorException;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.common.exception.UnauthorizedAccessException;
import com.example.auction.product.domain.Product;
import com.example.auction.product.repository.ProductRepository;
import com.example.auction.report.domain.Report;
import com.example.auction.report.domain.ReportCategory;
import com.example.auction.report.domain.ReportTargetType;
import com.example.auction.report.dto.AdminBlockedProductDto;
import com.example.auction.report.dto.ProductLiftRequest;
import com.example.auction.report.dto.ProductReportCreateDto;
import com.example.auction.report.repository.ReportRepository;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import com.example.auction.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class ProductReportService {

    private final ProductRepository productRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final StringRedisTemplate reportCounter;

    @Value("${report.product.threshold:5}")   // 기본 5건
    private long productReportThreshold;

    public ProductReportService(
            ProductRepository productRepository,
            ReportRepository reportRepository,
            UserRepository userRepository,
            UserService userService,
            @Qualifier("reportCounter") StringRedisTemplate reportCounter
    ) {
        this.productRepository = productRepository;
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.reportCounter = reportCounter;
    }

    private String productCountKey(Long productId) {
        return "report:product:count:{" + productId + "}";
    }

    private String currentEmailOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedAccessException("AUTH_REQUIRED", "로그인이 필요합니다.");
        }
        String email = auth.getName();
        if (email == null || email.isBlank() || "anonymousUser".equalsIgnoreCase(email)) {
            throw new UnauthorizedAccessException("AUTH_REQUIRED", "로그인이 필요합니다.");
        }
        return email;
    }

    private User currentUserOrThrow() {
        String email = currentEmailOrThrow();
        return userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> {
                    log.warn("[USER_NOT_FOUND] 존재하지 않거나 만료된 사용자 email={}", email);
                    return new ResourceNotFoundException(
                            "USER_NOT_FOUND",
                            "접속중인 계정을 찾을 수 없습니다. 고객센터에 문의해주세요."
                    );
                });
    }

    private long incrSafe(String key, long delta) {
        try {
            Long v = reportCounter.opsForValue().increment(key, delta);
            return v == null ? 0L : v;
        } catch (Exception e) {
            log.warn("[REPORT_COUNTER_FAIL] incr failed key={}, delta={}", key, delta, e);
            return 0L;
        }
    }

    private long getLongSafe(String key) {
        try {
            String v = reportCounter.opsForValue().get(key);
            if (v == null || v.isBlank()) return 0L;
            try {
                return Long.parseLong(v);
            } catch (NumberFormatException nfe) {
                log.warn("[REPORT_COUNTER_PARSE_FAIL] key={}, raw={}", key, v);
                return 0L;
            }
        } catch (Exception e) {
            log.warn("[REPORT_COUNTER_FAIL] get failed key={}", key, e);
            return 0L;
        }
    }

    private void resetSafe(String key) {
        try {
            reportCounter.delete(key);
        } catch (Exception e) {
            log.warn("[REPORT_COUNTER_FAIL] reset failed key={}", key, e);
        }
    }

    // [유저] 상품 신고
    @Transactional
    public void reportProduct(ProductReportCreateDto dto) {
        User reporter = currentUserOrThrow();

        if (dto == null || dto.getProductId() == null) {
            throw new IllegalArgumentException("상품 ID가 필요합니다.");
        }

        Long productId = dto.getProductId();

        Product product = productRepository.findByProductIdAndDelYn(productId, DelYN.N)
                .orElseThrow(() -> {
                    log.warn("[PRODUCT_NOT_FOUND] productId={}", productId);
                    return new ResourceNotFoundException(
                            "PRODUCT_NOT_FOUND",
                            "대상 상품이 존재하지 않거나 비활성화 상태입니다."
                    );
                });

        boolean dup;
        try {
            dup = reportRepository.existsByReporter_UserIdAndTargetTypeAndTargetId(
                    reporter.getUserId(),
                    ReportTargetType.PRODUCT,
                    productId
            );
        } catch (Exception e) {
            log.error("[REPORT_DUP_CHECK_FAIL] reporterId={}, productId={}", reporter.getUserId(), productId, e);
            throw new InternalErrorException(
                    "REPORT_DUP_CHECK_FAIL",
                    "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
            );
        }

        if (dup) {
            log.warn("[DUPLICATE_REPORT] reporterId={}, productId={}", reporter.getUserId(), productId);
            throw new UnauthorizedAccessException(
                    "DUPLICATE_REPORT",
                    "이미 해당 상품을 신고하셨습니다."
            );
        }

        try {
            Report report = Report.create(
                    reporter,
                    ReportTargetType.PRODUCT,
                    productId,
                    ReportCategory.OTHER,
                    dto.getContent()
            );
            reportRepository.save(report);
        } catch (Exception e) {
            log.error("[REPORT_SAVE_FAIL] reporterId={}, productId={}", reporter.getUserId(), productId, e);
            throw new InternalErrorException(
                    "REPORT_SAVE_FAIL",
                    "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
            );
        }

        long count = incrSafe(productCountKey(productId), 1L);

        try {
            if (count >= productReportThreshold && !Boolean.TRUE.equals(product.getBlocked())) {
                product.block("신고 임계치 초과(" + count + "건)");
                productRepository.save(product);
            }
        } catch (Exception e) {
            log.warn("[PRODUCT_BLOCK_FAIL] productId={}, count={}", productId, count, e);
        }
    }

    // [관리자] 차단된 상품 목록
    @Transactional(readOnly = true)
    public List<AdminBlockedProductDto> listBlockedProducts() {
        userService.checkAdminAuthority();

        try {
            return productRepository.findByBlockedAndDelYn(true, DelYN.N).stream()
                    .map(p -> AdminBlockedProductDto.builder()
                            .productId(p.getProductId())
                            .productName(p.getProductName())
                            .reportCount(getLongSafe(productCountKey(p.getProductId())))
                            .blockedAt(p.getBlockedAt())
                            .blockedReason(p.getBlockedReason())
                            .build())
                    .toList();
        } catch (Exception e) {
            log.error("[BLOCKED_PRODUCTS_LIST_FAIL]", e);
            throw new InternalErrorException(
                    "BLOCKED_PRODUCTS_LIST_FAIL",
                    "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
            );
        }
    }

    // [관리자] 차단 해제
    @Transactional
    public void liftProductBlock(ProductLiftRequest req) {
        userService.checkAdminAuthority();

        if (req == null || req.getProductId() == null) {
            throw new IllegalArgumentException("productId는 필수입니다.");
        }

        Long productId = req.getProductId();
        boolean reset = (req.getResetCounter() == null) ? true : req.getResetCounter();

        Product product = productRepository.findByProductIdAndDelYn(productId, DelYN.N)
                .orElseThrow(() -> {
                    log.warn("[PRODUCT_NOT_FOUND] productId={}", productId);
                    return new ResourceNotFoundException(
                            "PRODUCT_NOT_FOUND",
                            "대상 상품이 존재하지 않거나 비활성화 상태입니다."
                    );
                });

        try {
            product.unblock();

            // NOTE: set 지양이면 Product 도메인 메서드로 옮기기 권장
            if (req.getReason() != null && !req.getReason().isBlank()) {
                product.setBlockedReason(req.getReason().trim());
            }

            productRepository.save(product);
        } catch (Exception e) {
            log.error("[PRODUCT_UNBLOCK_FAIL] productId={}", productId, e);
            throw new InternalErrorException(
                    "PRODUCT_UNBLOCK_FAIL",
                    "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
            );
        }

        if (reset) {
            resetSafe(productCountKey(productId));
        }
    }
}