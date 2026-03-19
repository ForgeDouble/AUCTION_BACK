package com.example.auction.report.service;

import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.InternalErrorException;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.common.exception.UnauthorizedAccessException;
import com.example.auction.product.domain.Product;
import com.example.auction.product.repository.ProductRepository;
import com.example.auction.report.domain.Report;
import com.example.auction.report.domain.ReportCategory;
import com.example.auction.report.domain.ReportStatus;
import com.example.auction.report.domain.ReportTargetType;
import com.example.auction.report.dto.AdminBlockedProductDto;
import com.example.auction.report.dto.AdminProductReportGroupDto;
import com.example.auction.report.dto.AdminReportItemDto;
import com.example.auction.report.dto.AdminResolveDto;
import com.example.auction.report.dto.ProductLiftRequest;
import com.example.auction.report.dto.ProductReportCreateDto;
import com.example.auction.report.repository.ReportGroupProjection;
import com.example.auction.report.repository.ReportRepository;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import com.example.auction.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@Slf4j
public class ProductReportService {

    private final ProductRepository productRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final StringRedisTemplate reportCounter;

    @Value("${report.product.threshold:5}")
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

    private long decrFloorZeroSafe(String key, long delta) {
        try {
            Long v = reportCounter.opsForValue().increment(key, -delta);
            if (v == null) return 0L;
            if (v < 0L) {
                reportCounter.opsForValue().set(key, "0");
                return 0L;
            }
            return v;
        } catch (Exception e) {
            log.warn("[REPORT_COUNTER_FAIL] decr failed key={}, delta={}", key, delta, e);
            long next = Math.max(0L, getLongSafe(key) - delta);
            try {
                reportCounter.opsForValue().set(key, String.valueOf(next));
            } catch (Exception ignore) {
            }
            return next;
        }
    }

    private void resetSafe(String key) {
        try {
            reportCounter.delete(key);
        } catch (Exception e) {
            log.warn("[REPORT_COUNTER_FAIL] reset failed key={}", key, e);
        }
    }

    @Transactional
    public void reportProduct(ProductReportCreateDto dto) {
        User reporter = currentUserOrThrow();

        if (dto == null || dto.getProductId() == null) {
            throw new IllegalArgumentException("상품 ID가 필요합니다.");
        }

        Long productId = dto.getProductId();
        ReportCategory category = dto.getCategory() == null ? ReportCategory.OTHER : dto.getCategory();

        Product product = productRepository.findByProductIdAndDelYn(productId, DelYN.N)
                .orElseThrow(() -> {
                    log.warn("[PRODUCT_NOT_FOUND] productId={}", productId);
                    return new ResourceNotFoundException(
                            "PRODUCT_NOT_FOUND",
                            "대상 상품이 존재하지 않거나 비활성화 상태입니다."
                    );
                });

        if (product.getUser() != null && Objects.equals(product.getUser().getUserId(), reporter.getUserId())) {
            log.warn("[SELF_REPORT_FORBIDDEN] reporterId={}, productId={}", reporter.getUserId(), productId);
            throw new UnauthorizedAccessException(
                    "SELF_REPORT_FORBIDDEN",
                    "본인 상품은 신고할 수 없습니다."
            );
        }

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
                    category,
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

    @Transactional(readOnly = true)
    public List<AdminProductReportGroupDto> getAdminProductReportGroups(
            ReportCategory category,
            ReportStatus status,
            Integer minPending,
            Long productId
    ) {
        userService.checkAdminAuthority();

        List<ReportGroupProjection> rows;
        try {
            rows = reportRepository.aggregateReportGroupsByTargetType(ReportTargetType.PRODUCT);
        } catch (Exception e) {
            log.error("[PRODUCT_REPORT_GROUPS_AGG_FAIL]", e);
            throw new InternalErrorException(
                    "PRODUCT_REPORT_GROUPS_AGG_FAIL",
                    "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
            );
        }

        var filtered = rows.stream()
                .filter(p -> category == null || p.getCategory() == category)
                .filter(p -> productId == null || Objects.equals(p.getTargetId(), productId))
                .filter(p -> {
                    if (status == null) return true;
                    return switch (status) {
                        case PENDING -> p.getPendingCount() != null && p.getPendingCount() > 0;
                        case ACCEPTED -> p.getAcceptedCount() != null && p.getAcceptedCount() > 0;
                        case REJECTED -> p.getRejectedCount() != null && p.getRejectedCount() > 0;
                    };
                })
                .filter(p -> {
                    if (minPending == null) return true;
                    long pc = p.getPendingCount() == null ? 0L : p.getPendingCount();
                    return pc >= minPending;
                })
                .toList();

        Set<Long> ids = filtered.stream()
                .map(ReportGroupProjection::getTargetId)
                .collect(Collectors.toSet());

        final Map<Long, Product> productMap;
        try {
            if (ids.isEmpty()) {
                productMap = Collections.emptyMap();
            } else {
                productMap = StreamSupport.stream(productRepository.findAllById(ids).spliterator(), false)
                        .collect(Collectors.toMap(Product::getProductId, p -> p));
            }
        } catch (Exception e) {
            log.error("[PRODUCTS_LOAD_FAIL] idsSize={}", ids.size(), e);
            throw new InternalErrorException(
                    "PRODUCTS_LOAD_FAIL",
                    "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
            );
        }

        return filtered.stream()
                .map(p -> AdminProductReportGroupDto.fromEntity(p, productMap.get(p.getTargetId())))
                .sorted(
                        Comparator.comparing(AdminProductReportGroupDto::getPendingCount, Comparator.reverseOrder())
                                .thenComparing(
                                        AdminProductReportGroupDto::getLastReportedAt,
                                        Comparator.nullsLast(Comparator.reverseOrder())
                                )
                )
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<AdminReportItemDto> getProductGroupReportsDto(
            Long productId,
            ReportCategory category,
            Pageable pageable
    ) {
        userService.checkAdminAuthority();

        if (productId == null) throw new IllegalArgumentException("productId는 필수입니다.");
        if (category == null) throw new IllegalArgumentException("category는 필수입니다.");

        try {
            Page<Report> page = reportRepository.findByTargetTypeAndTargetIdAndCategory(
                    ReportTargetType.PRODUCT,
                    productId,
                    category,
                    pageable
            );
            return page.map(AdminReportItemDto::fromEntity);
        } catch (Exception e) {
            log.error("[PRODUCT_GROUP_REPORTS_LOAD_FAIL] productId={}, category={}", productId, category, e);
            throw new InternalErrorException(
                    "PRODUCT_GROUP_REPORTS_LOAD_FAIL",
                    "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
            );
        }
    }

    @Transactional
    public void adminResolveCategoryForProduct(Long productId, ReportCategory category, AdminResolveDto dto) {
        userService.checkAdminAuthority();

        if (productId == null) throw new IllegalArgumentException("productId는 필수입니다.");
        if (category == null) throw new IllegalArgumentException("카테고리를 지정해 주세요.");
        if (dto == null) throw new IllegalArgumentException("요청 본문이 비었습니다.");
        if (dto.getSuspendDays() != null) {
            throw new IllegalArgumentException("상품 신고 처리에서는 suspendDays를 사용할 수 없습니다.");
        }

        Product product = productRepository.findByProductIdAndDelYn(productId, DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PRODUCT_NOT_FOUND",
                        "대상 상품이 존재하지 않거나 비활성화 상태입니다."
                ));

        List<Report> pendings;
        try {
            pendings = reportRepository.findByTargetTypeAndTargetIdAndCategoryAndStatus(
                    ReportTargetType.PRODUCT,
                    productId,
                    category,
                    ReportStatus.PENDING
            );
        } catch (Exception e) {
            log.error("[PENDING_PRODUCT_REPORTS_LOAD_FAIL] productId={}, category={}", productId, category, e);
            throw new InternalErrorException(
                    "PENDING_PRODUCT_REPORTS_LOAD_FAIL",
                    "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
            );
        }

        if (pendings == null || pendings.isEmpty()) {
            throw new IllegalStateException("해당 상품(" + productId + ")의 " + category + " 카테고리에 대기중 신고가 없습니다.");
        }

        int size = pendings.size();

        if (dto.isAccept()) {
            try {
                pendings.forEach(r -> r.accept(dto.getAdminContent()));
                reportRepository.saveAll(pendings);
            } catch (Exception e) {
                log.error("[PRODUCT_REPORT_ACCEPT_SAVE_FAIL] productId={}, category={}, size={}", productId, category, size, e);
                throw new InternalErrorException(
                        "PRODUCT_REPORT_ACCEPT_SAVE_FAIL",
                        "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
                );
            }

            long current = getLongSafe(productCountKey(productId));
            try {
                if (current >= productReportThreshold && !Boolean.TRUE.equals(product.getBlocked())) {
                    product.block("신고 임계치 초과(" + current + "건)");
                    productRepository.save(product);
                }
            } catch (Exception e) {
                log.error("[PRODUCT_BLOCK_ON_ACCEPT_FAIL] productId={}, current={}", productId, current, e);
                throw new InternalErrorException(
                        "PRODUCT_BLOCK_ON_ACCEPT_FAIL",
                        "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
                );
            }

        } else {
            try {
                pendings.forEach(r -> r.reject(dto.getAdminContent()));
                reportRepository.saveAll(pendings);
            } catch (Exception e) {
                log.error("[PRODUCT_REPORT_REJECT_SAVE_FAIL] productId={}, category={}, size={}", productId, category, size, e);
                throw new InternalErrorException(
                        "PRODUCT_REPORT_REJECT_SAVE_FAIL",
                        "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
                );
            }

            long remain = decrFloorZeroSafe(productCountKey(productId), size);

            try {
                if (remain < productReportThreshold && Boolean.TRUE.equals(product.getBlocked())) {
                    product.unblock();
                    productRepository.save(product);
                }
            } catch (Exception e) {
                log.error("[PRODUCT_UNBLOCK_ON_REJECT_FAIL] productId={}, remain={}", productId, remain, e);
                throw new InternalErrorException(
                        "PRODUCT_UNBLOCK_ON_REJECT_FAIL",
                        "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
                );
            }
        }
    }

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

    @Transactional
    public void liftProductBlock(ProductLiftRequest req) {
        userService.checkAdminAuthority();

        if (req == null || req.getProductId() == null) {
            throw new IllegalArgumentException("productId는 필수입니다.");
        }

        Long productId = req.getProductId();
        boolean reset = req.getResetCounter() == null ? true : req.getResetCounter();

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