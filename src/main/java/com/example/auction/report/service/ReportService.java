package com.example.auction.report.service;

import com.example.auction.common.domain.DelYN;
import com.example.auction.notification.event.UserTemporarilyRestrictedEvent;
import com.example.auction.report.domain.Report;
import com.example.auction.report.domain.ReportCategory;
import com.example.auction.report.domain.ReportStatus;
import com.example.auction.report.domain.ReportTargetType;
import com.example.auction.report.dto.AdminReportGroupDto;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReportService {
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final UserReportAggregateRepository aggRepository;//
    private final UserService userService;

    private final StringRedisTemplate reportCounter;
    private final DefaultRedisScript<Long> movePendingToAcceptedScript;
    private final DefaultRedisScript<Long> safeDecrPendingScript;
    private final ApplicationEventPublisher publisher;

    public ReportService(ReportRepository reportRepository,
                         UserRepository userRepository,
                         UserReportAggregateRepository aggRepository,
                         UserService userService,
                         @Qualifier("reportCounter") StringRedisTemplate reportCounter,
                         @Qualifier("movePendingToAcceptedScript") DefaultRedisScript<Long> movePendingToAcceptedScript,
                         @Qualifier("safeDecrPendingScript") DefaultRedisScript<Long> safeDecrPendingScript, ApplicationEventPublisher publisher) {
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

    // 계산관련
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

    /* Redis 카운터 → DB 스냅샷 업서트 */
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

        boolean dup = reportRepository.existsByReporter_UserIdAndTargetTypeAndTargetId(
                reporter.getUserId(), ReportTargetType.USER, dto.getTargetId());
        if (dup) throw new RuntimeException("이미 해당 대상에 대해 신고하셨습니다.");

        Report report = dto.toEntity(reporter);
        reportRepository.save(report);

        long newPending = incr(pendingKey(dto.getTargetId(), dto.getCategory()), 1L);
        incr(totalPendingKey(dto.getTargetId()), 1L);

        if (newPending >= PENDING_THRESHOLD_PER_CATEGORY && Boolean.FALSE.equals(target.getViewOnly())) {
            target.makeViewOnly();
            userRepository.save(target);

            publisher.publishEvent(new UserTemporarilyRestrictedEvent(
                    target.getUserId(),
                    dto.getCategory(),
                    newPending,
                    "해당 카테고리 신고 임계치에 도달하여 임시 제한이 적용되었습니다."
            ));
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

        if (category == null) throw new IllegalArgumentException("카테고리를 지정해 주세요.");
        if (dto == null) throw new IllegalArgumentException("요청 본문이 비었습니다.");

        if (!dto.isAccept() && dto.getSuspendDays() != null) {
            throw new IllegalArgumentException("반려 처리에서는 정지일수를 지정할 수 없습니다.");
        }
        if (dto.isAccept() && dto.getSuspendDays() != null && dto.getSuspendDays() < 0) {
            throw new IllegalArgumentException("정지 일수는 0 이상이어야 합니다.");
        }

        User target = userRepository.findByUserIdAndDelYn(targetUserId, DelYN.N)
                .orElseThrow(() -> new RuntimeException("대상 유저가 존재하지 않거나 비활성화 상태입니다."));

        List<Report> pendings = reportRepository
                .findByTargetIdAndCategoryAndStatus(targetUserId, category, ReportStatus.PENDING);
        if (pendings.isEmpty()) {
            throw new IllegalStateException("해당 유저(" + targetUserId + ")의 " + category + " 카테고리에 대기중 신고가 없습니다.");
        }
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

    /* [관리자] 신고여부에 따른 그룹핑 조회 (신고당한사람 + 카테고리 )*/
    @Transactional(readOnly = true)
    public List<AdminReportGroupDto> getAdminReportGroups(ReportCategory category,
                                                          ReportStatus status,
                                                          Integer minPending,
                                                          Long targetUserId) {
        userService.checkAdminAuthority();

        var rows = reportRepository.aggregateReportGroupsByTargetType(ReportTargetType.USER);

        // 필터
        var filtered = rows.stream()
                .filter(projection -> category == null || projection.getCategory() == category)
                .filter(projection -> targetUserId == null || Objects.equals(projection.getTargetId(), targetUserId))
                .filter(projection -> {
                    if (status == null) return true;
                    return switch (status) {
                        case PENDING  -> (projection.getPendingCount()  != null && projection.getPendingCount()  > 0);
                        case ACCEPTED -> (projection.getAcceptedCount() != null && projection.getAcceptedCount() > 0);
                        case REJECTED -> (projection.getRejectedCount() != null && projection.getRejectedCount() > 0);
                    };
                })
                .filter(projection -> {
                    if (minPending == null) return true;
                    long pc = projection.getPendingCount() == null ? 0L : projection.getPendingCount();
                    return pc >= minPending;
                })
                .toList();

        // 타겟 정보 배치 조회
        var ids = filtered.stream().map(ReportGroupProjection::getTargetId).collect(Collectors.toSet());
        Map<Long, User> userMap = userRepository.findAllById(ids).stream()
                .filter(u -> u.getDelYn() == DelYN.N)
                .collect(Collectors.toMap(User::getUserId, Function.identity()));

        return filtered.stream()
                .map(projection -> AdminReportGroupDto.fromEntity(projection, userMap.get(projection.getTargetId())))
                .sorted(Comparator.comparing(AdminReportGroupDto::getLastReportedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /* [관리자] 그룹핑 조회에 따른 상세 조회*/
    @Transactional(readOnly = true)
    public Page<Report> getGroupReports(Long targetUserId, ReportCategory category, Pageable pageable) {
        userService.checkAdminAuthority();
        return reportRepository.findByTargetTypeAndTargetIdAndCategory(
                ReportTargetType.USER, targetUserId, category, pageable);
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
