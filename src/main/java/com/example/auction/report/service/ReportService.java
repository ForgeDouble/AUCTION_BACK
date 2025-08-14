package com.example.auction.report.service;

import com.example.auction.common.domain.DelYN;
import com.example.auction.report.domain.Report;
import com.example.auction.report.domain.ReportCategory;
import com.example.auction.report.domain.ReportStatus;
import com.example.auction.report.dto.ReportCreateDto;
import com.example.auction.report.repository.ReportRepository;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {
    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private static final int DUP_WINDOW_DAYS = 7;

    // 카테고리별 즉시 정지 일수
    private static final Map<ReportCategory, Integer> CATEGORY_IMMEDIATE_SUSPEND_DAYS = Map.of(
            ReportCategory.SPAM, 0,
            ReportCategory.AD, 0,
            ReportCategory.ABUSE, 3,
            ReportCategory.HATE, 7,
            ReportCategory.SCAM, 14,
            ReportCategory.OTHER, 0
    );

    // 카테고리별 경고 가중치
    private static final Map<ReportCategory, Long> CATEGORY_WARNING_WEIGHT = Map.of(
            ReportCategory.SPAM, 1L,
            ReportCategory.AD, 1L,
            ReportCategory.ABUSE, 3L,
            ReportCategory.HATE, 4L,
            ReportCategory.SCAM, 5L,
            ReportCategory.OTHER, 1L
    );

    // 누적 경고 임계
    private static final long WARN_TIER_WEEK  = 10L;
    private static final long WARN_TIER_MONTH = 20L;
    private static final long WARN_TIER_YEAR  = 30L;
    private static final long WARN_TIER_PERM  = 40L;

    // 탈퇴
    private static final long WARN_DELETE_THRESHOLD = 50L;

    public ReportService(UserRepository userRepository, ReportRepository reportRepository) {
        this.userRepository = userRepository;
        this.reportRepository = reportRepository;
    }


    /* 신고하기 */
    public void create(ReportCreateDto dto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User reporter = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 유저입니다."));

        if (dto.getReportedId() == null) throw new RuntimeException("신고 대상이 없습니다.");

        if (dto.getCategory() == null) throw new RuntimeException("신고 카테고리를 선택해 주세요.");

        // 피신고자 확인
        User reported = userRepository.findById(dto.getReportedId())
                .filter(u -> u.getDelYn() == DelYN.N)
                .orElseThrow(() -> new RuntimeException("신고 대상 유저가 존재하지 않거나 이미 탈퇴했습니다."));

        // 자기 자신 신고 금지
        if (reporter.getUserId().equals(reported.getUserId())) {
            throw new RuntimeException("본인을 신고할 수 없습니다.");
        }
        boolean dup = reportRepository
                .existsByReporter_UserIdAndReported_UserIdAndCategoryAndStatusInAndCreatedAtAfter(
                        reporter.getUserId(), reported.getUserId(), dto.getCategory(),
                        List.of(ReportStatus.PENDING, ReportStatus.ACCEPTED),
                        LocalDateTime.now().minusDays(DUP_WINDOW_DAYS)
                );
        if (dup) throw new RuntimeException("최근 동일 대상/카테고리 신고가 접수/승인 상태입니다.");

        reportRepository.save(dto.toEntity(reporter, reported));
    }


}
