package com.example.auction.board.service;

import com.example.auction.board.domain.Notice;
import com.example.auction.board.domain.NoticeAck;
import com.example.auction.board.domain.NoticeCategory;
import com.example.auction.board.dto.NoticeCreateRequest;
import com.example.auction.board.dto.NoticePageResponse;
import com.example.auction.board.dto.NoticeResponse;
import com.example.auction.board.dto.NoticeUpdateRequest;
import com.example.auction.board.repository.NoticeAckRepository;
import com.example.auction.board.repository.NoticeRepository;
import com.example.auction.common.auth.SecurityUserContext;
import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.BadRequestException;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.common.exception.UnauthorizedAccessException;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@Slf4j
public class AdminNoticeService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final NoticeRepository noticeRepository;
    private final NoticeAckRepository noticeAckRepository;
    private final UserRepository userRepository;

    public AdminNoticeService(NoticeRepository noticeRepository, NoticeAckRepository noticeAckRepository, UserRepository userRepository) {
        this.noticeRepository = noticeRepository;
        this.noticeAckRepository = noticeAckRepository;
        this.userRepository = userRepository;
    }

//    private void checkAdmin() {
//        var p = SecurityUserContext.principal();
//        if (p.getAuthority() == null || p.getAuthority() != Authority.ADMIN && p.getAuthority() != Authority.INQUIRY) {
//            throw new IllegalStateException("ADMIN 권한 혹은 INQUIRY 권한이 필요합니다.");
//        }
//    }
    // 로그인 유저 엔티티
    private User me() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> {
                    log.warn("[INVALID_USER] 존재하지 않거나 유효하지 않은 유저 email={}", email);
                    throw new UnauthorizedAccessException("INVALID_USER", "유효하지 않은 유저입니다.");
                });
    }

    // ADMIN/INQUIRY 권한 확인 + 엔티티 반환
    private User admin() {
        User user = me();
        if (user.getAuthority() != Authority.ADMIN && user.getAuthority() != Authority.INQUIRY) {
            log.warn("[UNAUTHORIZED_ACCESS] 관리자 외 권한 접근 userId={} authority={}", user.getUserId(), user.getAuthority());
            throw new UnauthorizedAccessException("UNAUTHORIZED_ACCESS", "관리자 외 권한이 없습니다.");
        }
        return user;
    }
    private Notice noticeOrThrow(Long id) {
        if (id == null) throw new BadRequestException("NOTICE_ID_REQUIRED", "noticeId가 필요합니다.");
        return noticeRepository.findById(id)
                .filter(x -> x.getDelYn() == DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException("NOTICE_NOT_FOUND", "공지(인수인계)가 존재하지 않습니다."));
    }

    // 인수인계 생성
    @Transactional
    public Long create(NoticeCreateRequest request) {
        User admin = admin();
//        User admin = currentAdminEntity();

        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new BadRequestException("TITLE_REQUIRED", "제목은 필수입니다.");
        }
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new BadRequestException("CONTENT_REQUIRED", "내용은 필수입니다.");
        }

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
        User admin = admin();
//        User admin = currentAdminEntity();

        int pg = Math.max(page, 0);
        int sz = Math.min(Math.max(size, 1), 200);

        Sort sort = Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("createdAt"));
        Pageable pageable = PageRequest.of(pg, sz, sort);

        Specification<Notice> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("delYn"), DelYN.N));

            if (category != null) predicates.add(cb.equal(root.get("category"), category));
            if (pinned != null) predicates.add(cb.equal(root.get("pinned"), pinned));

            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim() + "%";
                predicates.add(cb.or(cb.like(root.get("title"), like), cb.like(root.get("content"), like)));
            }
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
            if (to != null) predicates.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Notice> result = noticeRepository.findAll(specification, pageable);

        List<Long> noticeIds = result.getContent().stream().map(Notice::getId).toList();
        Set<Long> acked = noticeIds.isEmpty()
                ? Collections.emptySet()
                : new HashSet<>(noticeAckRepository.findAckedNoticeIds(admin.getUserId(), noticeIds));

        List<NoticeResponse> items = result.getContent().stream()
                .map(n -> NoticeResponse.fromEntity(n, acked.contains(n.getId())))
                .toList();

        return NoticePageResponse.builder()
                .items(items)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

//    @Transactional(readOnly = true)
//    public NoticeResponse detail(Long id) {
//        checkAdmin();
//        Notice notice = noticeRepository.findById(id)
//                .filter(x -> x.getDelYn() == DelYN.N)
//                .orElseThrow(() -> new ResourceNotFoundException("공지(인수인계)가 존재하지 않습니다."));
//        return NoticeResponse.fromEntity(notice);
//    }

    @Transactional
    public void update(Long id, NoticeUpdateRequest req) {
        admin();

//        if (req == null) throw new BadRequestException("INVALID_INPUT_FORMAT", "요청 바디가 비어있습니다.");

        Notice notice = noticeOrThrow(id);

        // 필요하면 제목/내용 null 방어 (도메인 update 정책에 맞춰 조정)
        if (req.getTitle() != null && req.getTitle().isBlank()) {
            throw new BadRequestException("TITLE_REQUIRED", "제목은 공백일 수 없습니다.");
        }
        if (req.getContent() != null && req.getContent().isBlank()) {
            throw new BadRequestException("CONTENT_REQUIRED", "내용은 공백일 수 없습니다.");
        }

        notice.update(
                req.getCategory(),
                req.getTitle(),
                req.getContent(),
                req.getPinned(),
                req.getImportance()
        );

        log.info("[NOTICE_UPDATED] noticeId={}", notice.getId());
    }

    @Transactional
    public void delete(Long id) {
        admin();
        Notice notice = noticeOrThrow(id);
        notice.setDelYn(DelYN.Y);
        notice.setDeletedAt(LocalDateTime.now(KST));

        log.info("[NOTICE_DELETED] noticeId={}", notice.getId());
    }

    @Transactional
    public void ack(Long noticeId) {
        User admin = admin();
//        User admin = currentAdminEntity();

        Notice notice = noticeOrThrow(noticeId);

        boolean exists = noticeAckRepository.existsByNotice_IdAndUser_UserId(notice.getId(), admin.getUserId());
        if (!exists) {
            noticeAckRepository.save(NoticeAck.of(notice, admin, LocalDateTime.now(KST)));
            log.info("[NOTICE_ACKED] noticeId={} adminId={}", notice.getId(), admin.getUserId());
        }
    }

}
