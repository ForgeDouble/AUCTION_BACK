package com.example.auction.board.service;

import com.example.auction.board.domain.Notice;
import com.example.auction.board.domain.NoticeCategory;
import com.example.auction.board.dto.NoticeCreateRequest;
import com.example.auction.board.repository.NoticeRepository;
import com.example.auction.common.auth.SecurityUserContext;
import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;

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
}
