package com.example.auction.report.service;

import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.InternalErrorException;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.common.exception.UnauthorizedAccessException;
import com.example.auction.notification.event.UserTemporarilyRestrictedEvent;
import com.example.auction.report.domain.Report;
import com.example.auction.report.domain.ReportCategory;
import com.example.auction.report.domain.ReportStatus;
import com.example.auction.report.domain.ReportTargetType;
import com.example.auction.report.dto.AdminReportGroupDto;
import com.example.auction.report.dto.AdminReportItemDto;
import com.example.auction.report.dto.AdminResolveDto;
import com.example.auction.report.dto.ReportCreateDto;
import com.example.auction.report.dto.ReportResponseDto;
import com.example.auction.report.repository.ReportGroupProjection;
import com.example.auction.report.repository.ReportRepository;
import com.example.auction.user.domain.User;
import com.example.auction.user.domain.UserReportAggregate;
import com.example.auction.user.repository.UserReportAggregateRepository;
import com.example.auction.user.repository.UserRepository;
import com.example.auction.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReportService {
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final UserReportAggregateRepository aggRepository;//
    private final UserService userService;

    private final StringRedisTemplate reportCounter;
    private final DefaultRedisScript<Long> movePendingToAcceptedScript;
    private final DefaultRedisScript<Long> safeDecrPendingScript;
    private final ApplicationEventPublisher publisher;

    private static final long PENDING_THRESHOLD_PER_CATEGORY = 5L;

    public ReportService(
            ReportRepository reportRepository,
            UserRepository userRepository,
            UserReportAggregateRepository aggRepository,
            UserService userService,
            @Qualifier("reportCounter") StringRedisTemplate reportCounter,
            @Qualifier("movePendingToAcceptedScript") DefaultRedisScript<Long> movePendingToAcceptedScript,
            @Qualifier("safeDecrPendingScript") DefaultRedisScript<Long> safeDecrPendingScript,
            ApplicationEventPublisher publisher
    ) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.aggRepository = aggRepository;
        this.userService = userService;
        this.reportCounter = reportCounter;
        this.movePendingToAcceptedScript = movePendingToAcceptedScript;
        this.safeDecrPendingScript = safeDecrPendingScript;
        this.publisher = publisher;
    }

    private String pendingKey(Long userId, ReportCategory category) {
        return "report:pending:{" + userId + ":" + category.name() + "}";
    }

    private String acceptedKey(Long userId, ReportCategory category) {
        return "report:accepted:{" + userId + ":" + category.name() + "}";
    }

    private String totalPendingKey(Long userId) {
        return "report:pending:total:{" + userId + "}";
    }

    private String currentEmailOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedAccessException("AUTH_REQUIRED", "로그인이 필요합니다.");
        }
        String email = auth.getName();
        if (email == null || email.isBlank() || "anonymousUser".equalsIgnoreCase(email)) {
            throw new UnauthorizedAccessException("AUTH_REQUIRED", "로그인이 필요합니다.");
        }
        return email;
    }

    private User currentUserOrThrow() {
        String email = currentEmailOrThrow();
        return userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> {
                    log.warn("[USER_NOT_FOUND] 존재하지 않거나 만료된 사용자 email={}", email);
                    return new ResourceNotFoundException(
                            "USER_NOT_FOUND",
                            "접속중인 계정을 찾을 수 없습니다. 고객센터에 문의해주세요."
                    );
                });
    }

    private long incrOrThrow(String key, long delta) {
        try {
            Long v = reportCounter.opsForValue().increment(key, delta);
            return (v == null) ? 0L : v;
        } catch (Exception e) {
            log.error("[REPORT_COUNTER_INCR_FAIL] key={}, delta={}", key, delta, e);
            throw new InternalErrorException(
                    "REPORT_COUNTER_INCR_FAIL",
                    "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
            );
        }
    }

    private long getLongSafe(String key) {
        try {
            String value = reportCounter.opsForValue().get(key);
            if (value == null || value.isBlank()) return 0L;
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException nfe) {
                log.warn("[REPORT_COUNTER_PARSE_FAIL] key={}, raw={}", key, value);
                return 0L;
            }
        } catch (Exception e) {
            log.warn("[REPORT_COUNTER_GET_FAIL] key={}", key, e);
            return 0L;
        }
    }

    private long getTotalPending(Long userId) {
        return getLongSafe(totalPendingKey(userId));
    }

    private void safeDecrOrThrow(String key, long n) {
        try {
            reportCounter.execute(
                    safeDecrPendingScript,
                    Collections.singletonList(key),
                    String.valueOf(n)
            );
        } catch (Exception e) {
            log.error("[REPORT_COUNTER_DECR_FAIL] key={}, n={}", key, n, e);
            throw new InternalErrorException(
                    "REPORT_COUNTER_DECR_FAIL",
                    "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
            );
        }
    }

    private void movePendingToAcceptedOrThrow(Long userId, ReportCategory category, long n) {
        try {
            reportCounter.execute(
                    movePendingToAcceptedScript,
                    Arrays.asList(pendingKey(userId, category), acceptedKey(userId, category)),
                    String.valueOf(n)
            );
        } catch (Exception e) {
            log.error("[REPORT_COUNTER_MOVE_FAIL] userId={}, category={}, n={}", userId, category, n, e);
            throw new InternalErrorException(
                    "REPORT_COUNTER_MOVE_FAIL",
                    "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
            );
        }
    }

    /* Redis 카운터 → DB 스냅샷 업서트 */
    @Transactional
    protected void upsertAggregateSnapshot(Long targetUserId, ReportCategory category) {
        try {
            long pending = getLongSafe(pendingKey(targetUserId, category));
            long accepted = getLongSafe(acceptedKey(targetUserId, category));

            UserReportAggregate agg = aggRepository.findByTargetUserIdAndCategory(targetUserId, category)
                    .orElseGet(() -> UserReportAggregate.init(targetUserId, category));

            agg.setPendingCount(pending);
            agg.setAcceptedCount(accepted);

            aggRepository.save(agg);
        } catch (Exception e) {
            log.error("[AGG_SNAPSHOT_UPSERT_FAIL] targetUserId={}, category={}", targetUserId, category, e);
            throw new InternalErrorException(
                    "AGG_SNAPSHOT_UPSERT_FAIL",
                    "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
            );
        }
    }

    /* 신고하기 */
    @Transactional
    public ReportResponseDto create(ReportCreateDto dto) {
        User reporter = currentUserOrThrow();

        if (dto == null) throw new IllegalArgumentException("요청 본문이 비었습니다.");
        if (dto.getTargetId() == null) throw new IllegalArgumentException("신고 대상이 없습니다.");
        if (dto.getCategory() == null) throw new IllegalArgumentException("신고 카테고리를 선택해 주세요.");

        Long targetUserId = dto.getTargetId();
        ReportCategory category = dto.getCategory();

        if (Objects.equals(targetUserId, reporter.getUserId())) {
            throw new UnauthorizedAccessException("SELF_REPORT_FORBIDDEN", "자기 자신을 신고할 수 없습니다.");
        }

        User target = userRepository.findByUserIdAndDelYn(targetUserId, DelYN.N)
                .orElseThrow(() -> {
                    log.warn("[TARGET_USER_NOT_FOUND] targetUserId={}", targetUserId);
                    return new ResourceNotFoundException(
                            "TARGET_USER_NOT_FOUND",
                            "대상 유저가 존재하지 않거나 비활성화 상태입니다."
                    );
                });

        boolean dup;
        try {
            dup = reportRepository.existsByReporter_UserIdAndTargetTypeAndTargetId(
                    reporter.getUserId(),
                    ReportTargetType.USER,
                    targetUserId
            );
        } catch (Exception e) {
            log.error("[REPORT_DUP_CHECK_FAIL] reporterId={}, targetUserId={}", reporter.getUserId(), targetUserId, e);
            throw new InternalErrorException(
                    "REPORT_DUP_CHECK_FAIL",
                    "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
            );
        }

        if (dup) {
            throw new UnauthorizedAccessException("DUPLICATE_REPORT", "이미 해당 대상에 대해 신고하셨습니다.");
        }

        Report report;
        try {
            report = dto.toEntity(reporter);
            reportRepository.save(report);
        } catch (Exception e) {
            log.error("[REPORT_SAVE_FAIL] reporterId={}, targetUserId={}", reporter.getUserId(), targetUserId, e);
            throw new InternalErrorException(
                    "REPORT_SAVE_FAIL",
                    "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
            );
        }

        long newPending = incrOrThrow(pendingKey(targetUserId, category), 1L);
        incrOrThrow(totalPendingKey(targetUserId), 1L);

        // 임계치 도달 시 viewOnly 적용
        if (newPending >= PENDING_THRESHOLD_PER_CATEGORY && !Boolean.TRUE.equals(target.getViewOnly())) {
            try {
                target.makeViewOnly();
                userRepository.save(target);

                try {
                    publisher.publishEvent(new UserTemporarilyRestrictedEvent(
                            target.getUserId(),
                            category,
                            newPending,
                            "해당 카테고리 신고 임계치에 도달하여 임시 제한이 적용되었습니다."
                    ));
                } catch (Exception ev) {
                    log.warn("[RESTRICT_EVENT_PUBLISH_FAIL] targetUserId={}, category={}", targetUserId, category, ev);
                }
            } catch (Exception e) {
                log.error("[VIEW_ONLY_APPLY_FAIL] targetUserId={}, category={}, pending={}",
                        targetUserId, category, newPending, e);
                throw new InternalErrorException(
                        "VIEW_ONLY_APPLY_FAIL",
                        "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
                );
            }
        }

        return ReportResponseDto.fromEntity(report);
    }

    /* 관리자: 카테고리 단위 승인/반려 */
    @Transactional
    public void adminResolveCategoryForUser(Long targetUserId, ReportCategory category, AdminResolveDto dto) {
        userService.checkAdminAuthority();

        if (targetUserId == null) throw new IllegalArgumentException("targetUserId는 필수입니다.");
        if (category == null) throw new IllegalArgumentException("카테고리를 지정해 주세요.");
        if (dto == null) throw new IllegalArgumentException("요청 본문이 비었습니다.");

        if (!dto.isAccept() && dto.getSuspendDays() != null) {
            throw new IllegalArgumentException("반려 처리에서는 정지일수를 지정할 수 없습니다.");
        }
        if (dto.isAccept() && dto.getSuspendDays() != null && dto.getSuspendDays() < 0) {
            throw new IllegalArgumentException("정지 일수는 0 이상이어야 합니다.");
        }

        User target = userRepository.findByUserIdAndDelYn(targetUserId, DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TARGET_USER_NOT_FOUND",
                        "대상 유저가 존재하지 않거나 비활성화 상태입니다."
                ));

        List<Report> pendings;
        try {
            pendings = reportRepository.findByTargetTypeAndTargetIdAndCategoryAndStatus(
                    ReportTargetType.USER, targetUserId, category, ReportStatus.PENDING
            );
        } catch (Exception e) {
            log.error("[PENDING_REPORTS_LOAD_FAIL] targetUserId={}, category={}", targetUserId, category, e);
            throw new InternalErrorException(
                    "PENDING_REPORTS_LOAD_FAIL",
                    "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
            );
        }

        if (pendings == null || pendings.isEmpty()) {
            throw new IllegalStateException("해당 유저(" + targetUserId + ")의 " + category + " 카테고리에 대기중 신고가 없습니다.");
        }

        int size = pendings.size();

        if (dto.isAccept()) {
            //  DB: ACCEPT
            try {
                pendings.forEach(r -> r.accept(dto.getAdminContent()));
                reportRepository.saveAll(pendings);
            } catch (Exception e) {
                log.error("[REPORT_ACCEPT_SAVE_FAIL] targetUserId={}, category={}, size={}", targetUserId, category, size, e);
                throw new InternalErrorException(
                        "REPORT_ACCEPT_SAVE_FAIL",
                        "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
                );
            }

            // Redis: pending -> accepted, totalPending 감소
            movePendingToAcceptedOrThrow(targetUserId, category, size);
            safeDecrOrThrow(totalPendingKey(targetUserId), size);

            // 제재
            try {
                if (dto.getSuspendDays() != null && dto.getSuspendDays() > 0) {
                    target.suspendUntil(LocalDateTime.now().plusDays(dto.getSuspendDays()));
                    target.cancelViewOnly();
                } else {
                    target.setWarning(target.getWarning() + 1);
                    // viewOnly 유지(정책에 따라 조정)
                }
                userRepository.save(target);
            } catch (Exception e) {
                log.error("[TARGET_PENALTY_APPLY_FAIL] targetUserId={}, category={}", targetUserId, category, e);
                throw new InternalErrorException(
                        "TARGET_PENALTY_APPLY_FAIL",
                        "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
                );
            }

        } else {
            // DB: REJECT
            try {
                pendings.forEach(r -> r.reject(dto.getAdminContent()));
                reportRepository.saveAll(pendings);
            } catch (Exception e) {
                log.error("[REPORT_REJECT_SAVE_FAIL] targetUserId={}, category={}, size={}", targetUserId, category, size, e);
                throw new InternalErrorException(
                        "REPORT_REJECT_SAVE_FAIL",
                        "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
                );
            }

            // Redis: pending 감소, totalPending 감소
            safeDecrOrThrow(pendingKey(targetUserId, category), size);
            safeDecrOrThrow(totalPendingKey(targetUserId), size);

            // 총합 임계 미만 -> viewOnly 해제
            long total = getTotalPending(targetUserId);
            if (total < PENDING_THRESHOLD_PER_CATEGORY && Boolean.TRUE.equals(target.getViewOnly())) {
                try {
                    target.cancelViewOnly();
                    userRepository.save(target);
                } catch (Exception e) {
                    log.error("[VIEW_ONLY_RELEASE_FAIL] targetUserId={}, totalPending={}", targetUserId, total, e);
                    throw new InternalErrorException(
                            "VIEW_ONLY_RELEASE_FAIL",
                            "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
                    );
                }
            }
        }

        upsertAggregateSnapshot(targetUserId, category);
    }

    /* [관리자] 신고 그룹(대상 유저 + 카테고리) 조회 */
    @Transactional(readOnly = true)
    public List<AdminReportGroupDto> getAdminReportGroups(
            ReportCategory category,
            ReportStatus status,
            Integer minPending,
            Long targetUserId
    ) {
        userService.checkAdminAuthority();

        List<ReportGroupProjection> rows;
        try {
            rows = reportRepository.aggregateReportGroupsByTargetType(ReportTargetType.USER);
        } catch (Exception e) {
            log.error("[REPORT_GROUPS_AGG_FAIL]", e);
            throw new InternalErrorException(
                    "REPORT_GROUPS_AGG_FAIL",
                    "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
            );
        }

        var filtered = rows.stream()
                .filter(p -> category == null || p.getCategory() == category)
                .filter(p -> targetUserId == null || Objects.equals(p.getTargetId(), targetUserId))
                .filter(p -> {
                    if (status == null) return true;
                    return switch (status) {
                        case PENDING -> (p.getPendingCount() != null && p.getPendingCount() > 0);
                        case ACCEPTED -> (p.getAcceptedCount() != null && p.getAcceptedCount() > 0);
                        case REJECTED -> (p.getRejectedCount() != null && p.getRejectedCount() > 0);
                    };
                })
                .filter(p -> {
                    if (minPending == null) return true;
                    long pc = (p.getPendingCount() == null) ? 0L : p.getPendingCount();
                    return pc >= minPending;
                })
                .toList();

        // 타겟 정보 배치 조회
        var ids = filtered.stream()
                .map(ReportGroupProjection::getTargetId)
                .collect(Collectors.toSet());

        final Map<Long, User> userMap;
        try {
            if (ids.isEmpty()) {
                userMap = Collections.emptyMap();
            } else {
                userMap = userRepository.findAllById(ids).stream()
                        .filter(u -> u.getDelYn() == DelYN.N)
                        .collect(Collectors.toMap(User::getUserId, Function.identity()));
            }
        } catch (Exception e) {
            log.error("[TARGET_USERS_LOAD_FAIL] idsSize={}", ids.size(), e);
            throw new InternalErrorException(
                    "TARGET_USERS_LOAD_FAIL",
                    "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
            );
        }

        return filtered.stream()
                .map(p -> AdminReportGroupDto.fromEntity(p, userMap.get(p.getTargetId())))
                .sorted(Comparator.comparing(
                        AdminReportGroupDto::getLastReportedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
    }

    /* [관리자] 그룹 상세(페이지) */
    @Transactional(readOnly = true)
    public Page<AdminReportItemDto> getGroupReportsDto(Long targetUserId, ReportCategory category, Pageable pageable) {
        userService.checkAdminAuthority();

        if (targetUserId == null) throw new IllegalArgumentException("targetUserId는 필수입니다.");
        if (category == null) throw new IllegalArgumentException("category는 필수입니다.");

        try {
            Page<Report> page = reportRepository.findByTargetTypeAndTargetIdAndCategory(
                    ReportTargetType.USER, targetUserId, category, pageable
            );
            return page.map(AdminReportItemDto::fromEntity);
        } catch (Exception e) {
            log.error("[GROUP_REPORTS_LOAD_FAIL] targetUserId={}, category={}", targetUserId, category, e);
            throw new InternalErrorException(
                    "GROUP_REPORTS_LOAD_FAIL",
                    "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
            );
        }
    }

    /* [관리자] 즉시 정지 */
    @Transactional
    public void adminSuspendUser(Long targetUserId, long days, String reason) {
        userService.checkAdminAuthority();

        if (targetUserId == null) throw new IllegalArgumentException("targetUserId는 필수입니다.");
        if (days < 0) throw new IllegalArgumentException("days는 0 이상이어야 합니다.");

        User target = userRepository.findByUserIdAndDelYn(targetUserId, DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TARGET_USER_NOT_FOUND",
                        "대상 유저가 존재하지 않거나 비활성화 상태입니다."
                ));

        try {
            target.suspendUntil(LocalDateTime.now().plusDays(days));
            target.cancelViewOnly();
            userRepository.save(target);
        } catch (Exception e) {
            log.error("[ADMIN_SUSPEND_FAIL] targetUserId={}, days={}", targetUserId, days, e);
            throw new InternalErrorException(
                    "ADMIN_SUSPEND_FAIL",
                    "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
            );
        }
    }

    /* [관리자] 상태 복구 */
    @Transactional
    public void adminLiftAll(Long targetUserId, String reason) {
        userService.checkAdminAuthority();

        if (targetUserId == null) throw new IllegalArgumentException("targetUserId는 필수입니다.");

        User target = userRepository.findByUserIdAndDelYn(targetUserId, DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TARGET_USER_NOT_FOUND",
                        "대상 유저가 존재하지 않거나 비활성화 상태입니다."
                ));

        try {
            target.liftSuspension();
            target.cancelViewOnly();
            userRepository.save(target);
        } catch (Exception e) {
            log.error("[ADMIN_LIFT_ALL_FAIL] targetUserId={}", targetUserId, e);
            throw new InternalErrorException(
                    "ADMIN_LIFT_ALL_FAIL",
                    "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
            );
        }
    }
}