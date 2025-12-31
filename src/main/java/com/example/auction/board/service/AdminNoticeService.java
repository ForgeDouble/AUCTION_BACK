package com.example.auction.board.service;

import com.example.auction.board.domain.Notice;
import com.example.auction.board.domain.NoticeCategory;
import com.example.auction.board.dto.NoticeCreateRequest;
import com.example.auction.board.dto.NoticePageResponse;
import com.example.auction.board.dto.NoticeResponse;
import com.example.auction.board.repository.NoticeRepository;
import com.example.auction.common.auth.SecurityUserContext;
import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminNoticeService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final NoticeRepository noticeRepository;
    private final UserRepository userRepository;

    public AdminNoticeService(NoticeRepository noticeRepository, UserRepository userRepository) {
        this.noticeRepository = noticeRepository;
        this.userRepository = userRepository;
    }

    private void checkAdmin() {
        var p = SecurityUserContext.principal();
        if (p.getAuthority() == null || p.getAuthority() != Authority.ADMIN) {
            throw new IllegalStateException("ADMIN 권한이 필요합니다.");
        }
    }

    private User currentAdminEntity() {
        var p = SecurityUserContext.principal();
        return userRepository.findByEmailAndDelYn(p.getEmail(), DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException("관리자 계정을 찾을 수 없습니다."));
    }

    // 인수인계 생성
    @Transactional
    public Long create(NoticeCreateRequest request) {
        checkAdmin();
        User admin = currentAdminEntity();

        if (request.getCategory() == null) request.setCategory(NoticeCategory.HANDOVER);
        if (request.getPinned() == null) request.setPinned(false);
        if (request.getImportance() == null) request.setImportance(50);

        Notice notice = Notice.builder()
                .author(admin)
                .category(request.getCategory())
                .title(request.getTitle())
                .content(request.getContent())
                .pinned(request.getPinned())
                .importance(Math.max(0, Math.min(100, request.getImportance())))
                .build();

        return noticeRepository.save(notice).getId();
    }
    @Transactional(readOnly = true)
    public NoticePageResponse list(NoticeCategory category, Boolean pinned, String q, LocalDate from, LocalDate to, int page, int size) {
        checkAdmin();

        int pg = Math.max(page, 0);
        int sz = Math.min(Math.max(size, 1), 50);

        Sort sort = Sort.by(
                Sort.Order.desc("pinned"),
                Sort.Order.desc("createdAt")
        );
        Pageable pageable = PageRequest.of(pg, sz, sort);

        Specification<Notice> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("delYn"), DelYN.N));

            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (pinned != null) {
                predicates.add(cb.equal(root.get("pinned"), pinned));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("title"), like),
                        cb.like(root.get("content"), like)
                ));
            }
            if (from != null) {
                LocalDateTime s = from.atStartOfDay();
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), s));
            }
            if (to != null) {
                LocalDateTime e = to.plusDays(1).atStartOfDay(); // to 포함
                predicates.add(cb.lessThan(root.get("createdAt"), e));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Notice> result = noticeRepository.findAll(spec, pageable);

        return NoticePageResponse.builder()
                .items(result.getContent().stream().map(NoticeResponse::fromEntity).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public NoticeResponse detail(Long id) {
        checkAdmin();
        Notice notice = noticeRepository.findById(id)
                .filter(x -> x.getDelYn() == DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException("공지(인수인계)가 존재하지 않습니다."));
        return NoticeResponse.fromEntity(notice);
    }


}
