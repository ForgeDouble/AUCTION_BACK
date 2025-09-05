package com.example.auction.report.controller;

import com.example.auction.common.dto.CommonResDto;
import com.example.auction.report.dto.AdminBlockedProductDto;
import com.example.auction.report.dto.ProductLiftRequest;
import com.example.auction.report.dto.ProductReportCreateDto;
import com.example.auction.report.service.ProductReportService;
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

    /* [유저] 상품 신고 */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/create")
    public ResponseEntity<CommonResDto> report(@RequestBody ProductReportCreateDto dto) {
        productReportService.reportProduct(dto);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "상품 신고 접수 완료", null));
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
