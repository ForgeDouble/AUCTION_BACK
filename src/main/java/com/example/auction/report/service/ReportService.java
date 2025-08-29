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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class ReportService {
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final UserReportAggregateRepository aggRepository;
    private final UserService userService;

    private final StringRedisTemplate reportCounter;
    private final DefaultRedisScript<Long> movePendingToAcceptedScript;
    private final DefaultRedisScript<Long> safeDecrPendingScript;


    public ReportService(ReportRepository reportRepository,
                         UserRepository userRepository,
                         UserReportAggregateRepository aggRepository,
                         UserService userService,
                         @Qualifier("reportCounter") StringRedisTemplate reportCounter,
                         @Qualifier("movePendingToAcceptedScript") DefaultRedisScript<Long> movePendingToAcceptedScript,
                         @Qualifier("safeDecrPendingScript") DefaultRedisScript<Long> safeDecrPendingScript) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.aggRepository = aggRepository;
        this.userService = userService;
        this.reportCounter = reportCounter;
        this.movePendingToAcceptedScript = movePendingToAcceptedScript;
        this.safeDecrPendingScript = safeDecrPendingScript;
    }

    // ---------- Redis Key Helper ----------
    private String pendingKey(Long userId, ReportCategory category) {
        return "report:pending:{" + userId + ":" + category.name() + "}";
    }
    private String acceptedKey(Long userId, ReportCategory category) {
        return "report:accepted:{" + userId + ":" + category.name() + "}";
    }
    private String totalPendingKey(Long userId) {
        return "report:pending:total:{" + userId + "}";
    }

    // ---------- Helpers ----------
    private long incr(String key, long delta) {
        Long v = reportCounter.opsForValue().increment(key, delta);
        return v == null ? 0L : v;
    }
    private long getLong(String key) {
        String v = reportCounter.opsForValue().get(key);
        return (v == null) ? 0L : Long.parseLong(v);
    }
    private long getTotalPending(Long userId) { return getLong(totalPendingKey(userId)); }
    private void safeDecr(String key, long n) {
        reportCounter.execute(safeDecrPendingScript, Collections.singletonList(key), String.valueOf(n));
    }
    private static final long PENDING_THRESHOLD_PER_CATEGORY = 5L;

    /** Redis 카운터 → DB 스냅샷 업서트 */
    @Transactional
    protected void upsertAggregateSnapshot(Long targetUserId, ReportCategory category) {
        long pending = getLong(pendingKey(targetUserId, category));
        long accepted = getLong(acceptedKey(targetUserId, category));
        UserReportAggregate agg = aggRepository.findByTargetUserIdAndCategory(targetUserId, category)
                .orElseGet(() -> UserReportAggregate.init(targetUserId, category));
        agg.setPendingCount(pending);
        agg.setAcceptedCount(accepted);
        aggRepository.save(agg);
    }

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

        long newPending = incr(pendingKey(dto.getTargetId(), dto.getCategory()), 1L);
        incr(totalPendingKey(dto.getTargetId()), 1L);

        if (newPending >= PENDING_THRESHOLD_PER_CATEGORY && Boolean.FALSE.equals(target.getViewOnly())) {
            target.makeViewOnly();
            userRepository.save(target);
        }

//        UserReportAggregate aggregate = aggRepository
//                .findByTargetUserIdAndCategory(dto.getTargetId(), dto.getCategory())
//                .orElse(UserReportAggregate.init(dto.getTargetId(), dto.getCategory()));
//        aggregate.increasePending();
//        aggRepository.save(aggregate);
//
//        // 임계치까지 신고 접수 시 조회가능 메서드
//        if (aggregate.getPendingCount() >= PENDING_THRESHOLD_PER_CATEGORY && !Boolean.TRUE.equals(target.getViewOnly())) {
//            target.makeViewOnly();
//            userRepository.save(target);
//        }

        return ReportResponseDto.fromEntity(report);
    }

    /* 관리자 카테고리에 대한 승인 & 취소 */
    @Transactional
    public void adminResolveCategoryForUser(Long targetUserId, ReportCategory category, AdminResolveDto dto) {
        userService.checkAdminAuthority();

        User target = userRepository.findByUserIdAndDelYn(targetUserId, DelYN.N)
                .orElseThrow(() -> new RuntimeException("대상 유저가 존재하지 않거나 비활성화 상태입니다."));

        List<Report> pendings = reportRepository
                .findByTargetIdAndCategoryAndStatus(targetUserId, category, ReportStatus.PENDING);
        if (pendings.isEmpty()) return;

        int size = pendings.size();

        if (dto.isAccept()) {
            // 1) 신고 ACCEPT
            pendings.forEach(r -> r.accept(dto.getAdminContent()));
            reportRepository.saveAll(pendings);

            // 2) Redis: pending→accepted 이동(카테고리 원자 이동), 총합 pending 감소
            reportCounter.execute(
                    movePendingToAcceptedScript,
                    Arrays.asList(pendingKey(targetUserId, category), acceptedKey(targetUserId, category)),
                    String.valueOf(size)
            );
            reportCounter.execute(
                    safeDecrPendingScript,
                    Collections.singletonList(totalPendingKey(targetUserId)),
                    String.valueOf(size)
            );

            // 3) 제재
            if (dto.getSuspendDays() != null && dto.getSuspendDays() > 0) {
                target.suspendUntil(LocalDateTime.now().plusDays(dto.getSuspendDays()));
                target.cancelViewOnly(); // 정책 따라 유지 가능
            } else {
                target.setWarning(target.getWarning() + 1);
                // viewOnly 유지 (정책에 따라 조정)
            }
            userRepository.save(target);

        } else {
            // REJECT: 신고 REJECT, 카테고리 pending 감소, 총합 pending 감소
            pendings.forEach(r -> r.reject(dto.getAdminContent()));
            reportRepository.saveAll(pendings);

            reportCounter.execute(
                    safeDecrPendingScript,
                    Collections.singletonList(pendingKey(targetUserId, category)),
                    String.valueOf(size)
            );
            reportCounter.execute(
                    safeDecrPendingScript,
                    Collections.singletonList(totalPendingKey(targetUserId)),
                    String.valueOf(size)
            );

            // 총합이 임계 미만이면 viewOnly 해제
            long total = getTotalPending(targetUserId);
            if (total < PENDING_THRESHOLD_PER_CATEGORY && Boolean.TRUE.equals(target.getViewOnly())) {
                target.cancelViewOnly();
                userRepository.save(target);
            }
        }

        upsertAggregateSnapshot(targetUserId, category);
    }

    /* [관리자] 즉시 정지 */
    @Transactional
    public void adminSuspendUser(Long targetUserId, long days, String reason) {
        userService.checkAdminAuthority();

        User target = userRepository.findByUserIdAndDelYn(targetUserId, DelYN.N)
                .orElseThrow(() -> new RuntimeException("대상 유저가 존재하지 않거나 비활성화 상태입니다."));

        target.suspendUntil(LocalDateTime.now().plusDays(days));
        target.cancelViewOnly();
        userRepository.save(target);
    }

    /* 상태 복구 */
    @Transactional
    public void adminLiftAll(Long targetUserId, String reason) {
        userService.checkAdminAuthority();

        User target = userRepository.findByUserIdAndDelYn(targetUserId, DelYN.N)
                .orElseThrow(() -> new RuntimeException("대상 유저가 존재하지 않거나 비활성화 상태입니다."));

        target.liftSuspension();
        target.cancelViewOnly();

        userRepository.save(target);
    }

}
