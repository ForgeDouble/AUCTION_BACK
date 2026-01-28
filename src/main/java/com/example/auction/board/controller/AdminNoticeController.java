package com.example.auction.board.controller;

import com.example.auction.board.domain.NoticeCategory;
import com.example.auction.board.dto.NoticeCreateRequest;
import com.example.auction.board.dto.NoticePageResponse;
import com.example.auction.board.dto.NoticeResponse;
import com.example.auction.board.dto.NoticeUpdateRequest;
import com.example.auction.board.service.AdminNoticeService;
import com.example.auction.common.dto.CommonResDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/notices")

public class AdminNoticeController {

    private final AdminNoticeService adminNoticeService;

    // 생성
    @PreAuthorize("hasAnyRole('ADMIN','INQUIRY')")
    @PostMapping
    public ResponseEntity<?> create(@RequestBody NoticeCreateRequest noticeCreateRequest) {
        Long id = adminNoticeService.create(noticeCreateRequest);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "공지(인수인계) 생성 완료", id));
    }

    // 페이징 보기(리스트)
    @PreAuthorize("hasAnyRole('ADMIN','INQUIRY')")
    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(name = "category", required = false) NoticeCategory category,
            @RequestParam(name = "pinned", required = false) Boolean pinned,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        NoticePageResponse noticePageResponse = adminNoticeService.list(category, pinned, q, from, to, page, size);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "공지(인수인계) 목록", noticePageResponse));
    }

    // 상세보기
//    @PreAuthorize("hasRole('ADMIN')")
//    @GetMapping("/{id}")
//    public ResponseEntity<?> detail(@PathVariable Long id) {
//        NoticeResponse res = adminNoticeService.detail(id);
//        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "공지(인수인계) 상세", res));
//    }

    // 수정하기
    @PreAuthorize("hasAnyRole('ADMIN','INQUIRY')")
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody NoticeUpdateRequest noticeUpdateRequest) {
        adminNoticeService.update(id, noticeUpdateRequest);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "공지(인수인계) 수정 완료", null));
    }

    // 삭제
    @PreAuthorize("hasAnyRole('ADMIN','INQUIRY')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        adminNoticeService.delete(id);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "공지(인수인계) 삭제 완료", null));
    }

    @PreAuthorize("hasAnyRole('ADMIN','INQUIRY')")
    @PostMapping("/{id}/ack")
    public ResponseEntity<?> ack(@PathVariable Long id) {
        adminNoticeService.ack(id);
        return ResponseEntity.noContent().build();
    }
}
