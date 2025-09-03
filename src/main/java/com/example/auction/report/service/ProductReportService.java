package com.example.auction.report.service;

import com.example.auction.common.domain.DelYN;
import com.example.auction.product.domain.Product;
import com.example.auction.product.repository.ProductRepository;
import com.example.auction.report.domain.Report;
import com.example.auction.report.domain.ReportCategory;
import com.example.auction.report.domain.ReportTargetType;
import com.example.auction.report.dto.ProductReportCreateDto;
import com.example.auction.report.repository.ReportRepository;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import com.example.auction.user.service.UserService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductReportService {

    private final ProductRepository productRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final StringRedisTemplate reportCounter;

    @Value("${report.product.threshold:5}")   // 기본 5건
    private long productReportThreshold;

    public ProductReportService(ProductRepository productRepository,
                                ReportRepository reportRepository,
                                UserRepository userRepository,
                                UserService userService,
                                @Qualifier("reportCounter") StringRedisTemplate reportCounter) {
        this.productRepository = productRepository;
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.reportCounter = reportCounter;
    }

    private String productCountKey(Long productId) {
        return "report:product:count:{" + productId + "}";
    }
    private long incr(String key, long delta) {
        Long v = reportCounter.opsForValue().increment(key, delta);
        return v == null ? 0L : v;
    }
    private long getLong(String key) {
        String v = reportCounter.opsForValue().get(key);
        return (v == null) ? 0L : Long.parseLong(v);
    }
    private void reset(String key) { reportCounter.delete(key); }

    // [유저] 상품 신고
    @Transactional
    public void reportProduct(ProductReportCreateDto dto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User reporter = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 유저입니다."));

        if (dto.getProductId() == null) throw new IllegalArgumentException("상품 ID가 필요합니다.");

        Product product = productRepository.findByProductIdAndDelYn(dto.getProductId(), DelYN.N)
                .orElseThrow(() -> new RuntimeException("대상 상품이 존재하지 않거나 비활성화 상태입니다."));

        // 같은 유저가 같은 상품을 중복 신고 못 하게(카테고리 불문)
        boolean dup = reportRepository.existsByReporter_UserIdAndTargetTypeAndTargetId(
                reporter.getUserId(), ReportTargetType.PRODUCT, dto.getProductId());
        if (dup) throw new RuntimeException("이미 해당 상품을 신고하셨습니다.");

        Report report = Report.create(reporter, ReportTargetType.PRODUCT, dto.getProductId(),
                ReportCategory.OTHER, dto.getContent());
        reportRepository.save(report);

        long count = incr(productCountKey(dto.getProductId()), 1L);
        if (count >= productReportThreshold && !Boolean.TRUE.equals(product.getBlocked())) {
            product.block("신고 임계치 초과(" + count + "건)");
            productRepository.save(product);
        }
    }


}
