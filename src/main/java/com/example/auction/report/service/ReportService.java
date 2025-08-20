package com.example.auction.report.service;

import com.example.auction.common.domain.DelYN;
import com.example.auction.report.domain.Report;
import com.example.auction.report.domain.ReportCategory;
import com.example.auction.report.domain.ReportStatus;
import com.example.auction.report.dto.AdminResolveDto;
import com.example.auction.report.dto.ReportCreateDto;
import com.example.auction.report.dto.ReportResponseDto;
import com.example.auction.report.repository.ReportRepository;
import com.example.auction.user.domain.User;
import com.example.auction.user.domain.UserReportAggregate;
import com.example.auction.user.repository.UserReportAggregateRepository;
import com.example.auction.user.repository.UserRepository;
import com.example.auction.user.service.UserService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReportService {
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final UserReportAggregateRepository aggRepository;
    private final UserService userService;


    public ReportService(ReportRepository reportRepository, UserRepository userRepository, UserReportAggregateRepository aggRepository, UserService userService) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.aggRepository = aggRepository;
        this.userService = userService;
    }

    private static final long PENDING_THRESHOLD_PER_CATEGORY = 5L;


    /* 신고하기 */
    @Transactional
    public ReportResponseDto create(ReportCreateDto dto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User reporter = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 유저입니다."));

        if (dto.getTargetId() == null) throw new RuntimeException("신고 대상이 없습니다.");
        if (dto.getCategory() == null) throw new RuntimeException("신고 카테고리를 선택해 주세요.");
        if (dto.getTargetId().equals(reporter.getUserId()))
            throw new RuntimeException("자기 자신을 신고할 수 없습니다.");

        User target = userRepository.findByUserIdAndDelYn(dto.getTargetId(), DelYN.N)
                .orElseThrow(() -> new RuntimeException("대상 유저가 존재하지 않거나 비활성화 상태입니다."));

        boolean dup = reportRepository.existsByReporter_UserIdAndTargetIdAndCategory(
                reporter.getUserId(), dto.getTargetId(), dto.getCategory());
        if (dup) throw new RuntimeException("이미 해당 카테고리로 신고하셨습니다.");

        Report report = dto.toEntity(reporter);
        reportRepository.save(report);

        UserReportAggregate agg = aggRepository
                .findByTargetUserIdAndCategory(dto.getTargetId(), dto.getCategory())
                .orElse(UserReportAggregate.init(dto.getTargetId(), dto.getCategory()));
        agg.incPending();
        aggRepository.save(agg);

        // 임계치까지 신고 접수 시 조회가능 메서드
        if (agg.getPendingCount() >= PENDING_THRESHOLD_PER_CATEGORY && !Boolean.TRUE.equals(target.getViewOnly())) {
            target.makeViewOnly();
            userRepository.save(target);
        }

        return ReportResponseDto.fromEntity(report);
    }

    /* 관리자 승인 / 취소 */
    @Transactional
    public void adminResolveCategoryForUser(Long targetUserId, ReportCategory category, AdminResolveDto dto) {
        userService.checkAdminAuthority();

        User target = userRepository.findByUserIdAndDelYn(targetUserId, DelYN.N)
                .orElseThrow(() -> new RuntimeException("대상 유저가 존재하지 않거나 비활성화 상태입니다."));

        List<Report> pendings = reportRepository.findByTargetIdAndStatus(targetUserId, ReportStatus.PENDING)
                .stream().filter(r -> r.getCategory() == category).toList();
        if (pendings.isEmpty()) return;

        UserReportAggregate aggregate = aggRepository
                .findByTargetUserIdAndCategory(targetUserId, category)
                .orElseThrow(() -> new RuntimeException("집계 정보가 없습니다."));

        if (dto.isAccept()) {
            // 승인 관련
            pendings.forEach(r -> r.accept(dto.getAdminContent()));
            reportRepository.saveAll(pendings);

            aggregate.movePendingToAccepted(pendings.size());
            aggRepository.save(aggregate);

            if (dto.getSuspendDays() != null && dto.getSuspendDays() > 0) {
                target.suspendUntil(LocalDateTime.now().plusDays(dto.getSuspendDays()));
                target.cancelViewOnly();
            } else {
                target.setWarning(target.getWarning() + 1);
                // viewOnly 유지/해제는 정책에 따라 다를 수 있음(여기서는 유지)
            }
            userRepository.save(target);

        } else {
            //  취소 관련
            pendings.forEach(r -> r.reject(dto.getAdminContent()));
            reportRepository.saveAll(pendings);

            aggregate.decPending(pendings.size());
            aggRepository.save(aggregate);

            // 임계치 미만이면 임시정지 해제
            if (aggregate.getPendingCount() < PENDING_THRESHOLD_PER_CATEGORY && Boolean.TRUE.equals(target.getViewOnly())) {
                target.cancelViewOnly();
                userRepository.save(target);
            }
        }
    }

}
