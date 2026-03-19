package com.example.auction.report.controller;

import com.example.auction.common.dto.CommonResDto;
import com.example.auction.report.domain.ReportCategory;
import com.example.auction.report.domain.ReportStatus;
import com.example.auction.report.dto.*;
import com.example.auction.report.service.ProductReportService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/report/product")
public class ProductReportController {

    private final ProductReportService productReportService;

    public ProductReportController(ProductReportService productReportService) {
        this.productReportService = productReportService;
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/create")
    public ResponseEntity<CommonResDto> report(@RequestBody ProductReportCreateDto dto) {
        productReportService.reportProduct(dto);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "상품 신고 접수 완료", null));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/groups")
    public ResponseEntity<CommonResDto> getProductReportGroups(
            @RequestParam(required = false) ReportCategory category,
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) Integer minPending,
            @RequestParam(required = false) Long productId
    ) {
        List<AdminProductReportGroupDto> list =
                productReportService.getAdminProductReportGroups(category, status, minPending, productId);

        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "상품 신고 그룹 목록 조회 성공", list));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/groups/{productId}/{category}")
    public ResponseEntity<CommonResDto> getProductGroupReports(
            @PathVariable Long productId,
            @PathVariable ReportCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        var pageRes = productReportService.getProductGroupReportsDto(productId, category, pageable);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "상품 신고 상세 조회 성공", pageRes));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/resolve/{productId}/{category}")
    public ResponseEntity<CommonResDto> resolveProductGroup(
            @PathVariable Long productId,
            @PathVariable ReportCategory category,
            @RequestBody AdminResolveDto dto
    ) {
        productReportService.adminResolveCategoryForProduct(productId, category, dto);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "상품 신고 처리 완료", null));
    }

    /* [관리자] 차단된 상품 목록 */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/blocked")
    public ResponseEntity<CommonResDto> blockedList() {
        List<AdminBlockedProductDto> list = productReportService.listBlockedProducts();
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "차단 상품 목록 조회 성공", list));
    }

    /* [관리자] 차단 해제 */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/lift")
    public ResponseEntity<CommonResDto> lift(@RequestBody ProductLiftRequest dto) {
        productReportService.liftProductBlock(dto);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "차단 해제 완료", null));
    }
}