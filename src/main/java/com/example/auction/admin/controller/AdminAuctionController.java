package com.example.auction.admin.controller;

import com.example.auction.admin.dto.AdminAuctionActionReq;
import com.example.auction.admin.service.AdminAuctionService;
import com.example.auction.common.dto.CommonResDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/auctions")
public class AdminAuctionController {

    private final AdminAuctionService adminAuctionService;

//    @PreAuthorize("hasAuthority('ADMIN') or hasRole('ADMIN')")
    @PreAuthorize("hasAnyRole('ADMIN','INQUIRY')")
    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var pg = adminAuctionService.list(page, size);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "경매 모니터링 조회 성공", pg));
    }

//    @PreAuthorize("hasAuthority('ADMIN') or hasRole('ADMIN')")
    @PreAuthorize("hasAnyRole('ADMIN','INQUIRY')")
    @PostMapping("/{productId}/suspend")
    public ResponseEntity<?> suspend(@PathVariable long productId,
                                     @RequestBody(required = false) AdminAuctionActionReq req) {
        adminAuctionService.suspend(productId, req);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "경매 임시차단 성공", null));
    }

//    @PreAuthorize("hasAuthority('ADMIN') or hasRole('ADMIN')")
    @PreAuthorize("hasAnyRole('ADMIN','INQUIRY')")
    @PostMapping("/{productId}/force-end")
    public ResponseEntity<?> forceEnd(@PathVariable long productId,
                                      @RequestBody(required = false) AdminAuctionActionReq req) {
        adminAuctionService.forceEnd(productId, req);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "경매 강제종료 성공", null));
    }
}
