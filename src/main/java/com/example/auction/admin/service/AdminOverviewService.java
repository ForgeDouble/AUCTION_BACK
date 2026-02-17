package com.example.auction.admin.service;

import com.example.auction.admin.dto.AdminCategoryDistributionDto;
import com.example.auction.admin.dto.AdminOverviewResponse;
import com.example.auction.bid.domain.IsWinned;
import com.example.auction.bid.repository.BidRepository;
import com.example.auction.category.domain.Category;
import com.example.auction.category.repository.CategoryRepository;
import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.BadRequestException;
import com.example.auction.common.exception.InternalErrorException;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.common.exception.UnauthorizedAccessException;
import com.example.auction.product.domain.Status;
import com.example.auction.product.repository.CategoryCountRow;
import com.example.auction.product.repository.ProductRepository;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import com.example.auction.user.service.UserStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;


@Service
@Slf4j
public class AdminOverviewService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BidRepository bidRepository;
    private final UserStatusService userStatusService;
    private final AdminReportCounter adminReportCounter;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public AdminOverviewService(UserRepository userRepository, ProductRepository productRepository, CategoryRepository categoryRepository, BidRepository bidRepository, UserStatusService userStatusService, AdminReportCounter adminReportCounter) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.bidRepository = bidRepository;
        this.userStatusService = userStatusService;
        this.adminReportCounter = adminReportCounter;
    }

    private static final List<String> TOP_LEVEL = List.of(
            "전자제품",
            "패션/잡화",
            "생활/가전",
            "취미/레저",
            "컬렉터블",
            "자동차/오토바이",
            "도서/음반/영화"
    );
    private String currentEmailOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank() || "anonymousUser".equals(auth.getName())) {
            throw new UnauthorizedAccessException("UNAUTHENTICATED", "로그인이 필요합니다.");
        }
        return auth.getName();
    }
    private User ensureAdmin() {
        String email = currentEmailOrThrow();

        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new UnauthorizedAccessException("INVALID_USER", "유효하지 않은 유저입니다."));

        if (user.getAuthority() != Authority.ADMIN && user.getAuthority() != Authority.INQUIRY) {
            throw new UnauthorizedAccessException("UNAUTHORIZED_ACCESS", "관리자 외 권한이 없습니다.");
        }
        return user;
    }

    public AdminOverviewResponse getOverview() {
        ensureAdmin();

        LocalDate today = LocalDate.now(KST);
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        try {
            long todayNewUsers = userRepository.countByCreatedAtBetween(start, end);
            long todayCreatedAuctions = productRepository.countByCreatedAtBetween(start, end);

            long todaySold = productRepository.countByStatusAndUpdatedAtBetween(Status.SELLED, start, end);
            long todayNotSold = productRepository.countByStatusAndUpdatedAtBetween(Status.NOTSELLED, start, end);
            long todayEndedAuctions = todaySold + todayNotSold;

            long ongoingAuctions = productRepository.countByStatusAndBlockedFalse(Status.PROCESSING);
            long totalBids = bidRepository.count();

            long realtimeUsers = safeRealtimeUsers();
            long todayActiveUsers = safeTodayActiveUsers(today);
            long reportsOpen = safeReportsOpen();

    //        long todayTradeAmount = bidRepository.sumWinningAmountForSoldProductsBetween(
    //                IsWinned.Y, Status.SELLED, start, end
    //        );
    //
    //        // 최근 6개월 월 평균 거래금액(낙찰 합 기준)
    //        long monthlyAvgTradeAmount = calcMonthlyAvg6();
            long todayTradeAmount = calcTodayGmv();
            long monthlyAvgTradeAmount = calcMonthlyAvgGmv(6);

            List<AdminOverviewResponse.HourlyPoint> hourly = safeHourlySeries(today);

            // 상태 분포(차단 제외)
            long statusReady = productRepository.countByStatusExcludingBlocked(Status.READY);
            long statusProcessing = productRepository.countByStatusExcludingBlocked(Status.PROCESSING);
            long statusSelled = productRepository.countByStatusExcludingBlocked(Status.SELLED);
            long statusNotselled = productRepository.countByStatusExcludingBlocked(Status.NOTSELLED);

            return AdminOverviewResponse.builder()
                    .todayNewUsers(todayNewUsers)
                    .todayCreatedAuctions(todayCreatedAuctions)
                    .todayEndedAuctions(todayEndedAuctions)
                    .todaySoldAuctions(todaySold)

                    .totalBids(totalBids)
                    .ongoingAuctions(ongoingAuctions)
                    .reportsOpen(reportsOpen)

                    .realtimeUsers(realtimeUsers)
                    .todayActiveUsers(todayActiveUsers)

                    .todayTradeAmount(todayTradeAmount)
                    .monthlyAvgTradeAmount(monthlyAvgTradeAmount)

                    .todayActivityHourly(hourly)

                    .statusReady(statusReady)
                    .statusProcessing(statusProcessing)
                    .statusSelled(statusSelled)
                    .statusNotselled(statusNotselled)
                    .build();
        } catch (UnauthorizedAccessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("[ADMIN_OVERVIEW_FAILED] today={} start={} end={}", today, start, end, e);
            throw new InternalErrorException("ADMIN_OVERVIEW_FAILED", "관리자 오버뷰 조회 중 오류가 발생했습니다.");
        }
    }

    private long safeRealtimeUsers() {
        try {
            return userStatusService.getRealtimeUsersCount();
        } catch (Exception e) {
            log.warn("[ADMIN_OVERVIEW_REALTIME_USERS_FAIL]", e);
            return 0L;
        }
    }

    private long safeTodayActiveUsers(LocalDate today) {
        try {
            return userStatusService.getDailyActiveCount(today);
        } catch (Exception e) {
            log.warn("[ADMIN_OVERVIEW_TODAY_ACTIVE_USERS_FAIL] today={}", today, e);
            return 0L;
        }
    }

    private long safeReportsOpen() {
        try {
            return adminReportCounter.getOpenReportsCount();
        } catch (Exception e) {
            log.warn("[ADMIN_OVERVIEW_REPORTS_OPEN_FAIL]", e);
            return 0L;
        }
    }

    private List<AdminOverviewResponse.HourlyPoint> safeHourlySeries(LocalDate today) {
        try {
            List<AdminOverviewResponse.HourlyPoint> hourly = new ArrayList<>();
            for (UserStatusService.HourlyPoint p : userStatusService.getHourlySeries(today)) {
                hourly.add(new AdminOverviewResponse.HourlyPoint(p.hour(), p.users()));
            }
            return hourly;
        } catch (Exception e) {
            log.warn("[ADMIN_OVERVIEW_HOURLY_SERIES_FAIL] today={}", today, e);
            return List.of();
        }
    }

    private long calcMonthlyAvg6() {
        LocalDate now = LocalDate.now(KST);
        LocalDate firstOfThisMonth = now.withDayOfMonth(1);

        LocalDate fromDate = firstOfThisMonth.minusMonths(5); // 포함 셑팅
        LocalDate toDate = firstOfThisMonth.plusMonths(1); // 포함 x

        LocalDateTime from = fromDate.atStartOfDay();
        LocalDateTime to = toDate.atStartOfDay();

        List<BidRepository.MonthlyTotalProjection> rows =
                bidRepository.findMonthlyWinningTotals(IsWinned.Y, Status.SELLED, from, to);

        Map<String, Long> ymToTotal = new HashMap<>();
        for (var r : rows) {
            ymToTotal.put(r.getYm(), (r.getTotal() == null ? 0L : r.getTotal()));
        }

        long sum = 0;
        // 6개월 고정셋팅 -> 평균(데이터 없는 달은 0)
        for (int i = 5; i >= 0; i--) {
            LocalDate day = firstOfThisMonth.minusMonths(i);
            String ym = day.getYear() + "-" + String.format("%02d", day.getMonthValue());
            sum += ymToTotal.getOrDefault(ym, 0L);
        }
        return sum / 6;
    }

    @Transactional(readOnly = true)
    public List<AdminCategoryDistributionDto> getTopLevelCategoryDistribution() {
        ensureAdmin();

        try {
            List<CategoryCountRow> rows = productRepository.countByCategoryIdExcludingDeletedBlocked();

            if (rows == null || rows.isEmpty()) {
                return TOP_LEVEL.stream()
                        .map(name -> new AdminCategoryDistributionDto(name, 0L))
                        .toList();
            }

            // leafCategoryId -> count
            Map<Long, Long> leafCounts = new HashMap<>();
            for (CategoryCountRow r : rows) {
                if (r == null || r.getCategoryId() == null) continue;
                long cnt = (r.getCnt() == null ? 0L : r.getCnt());
                if (cnt < 0) cnt = 0;
                leafCounts.put(r.getCategoryId(), cnt);
            }

            if (leafCounts.isEmpty()) {
                return TOP_LEVEL.stream()
                        .map(name -> new AdminCategoryDistributionDto(name, 0L))
                        .toList();
            }

            Map<Long, Category> leafMap = categoryRepository.findAllById(leafCounts.keySet())
                    .stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(Category::getCategoryId, c -> c, (a, b) -> a));

            // rootName 기준 합산
            Map<String, Long> rootSum = new HashMap<>();
            for (Map.Entry<Long, Long> e : leafCounts.entrySet()) {
                Category leaf = leafMap.get(e.getKey());
                if (leaf == null) continue;

                String rootName = resolveRootName(leaf);
                if (rootName == null || rootName.isBlank()) continue;

                rootSum.merge(rootName, e.getValue(), Long::sum);
            }

            return TOP_LEVEL.stream()
                    .map(name -> new AdminCategoryDistributionDto(name, rootSum.getOrDefault(name, 0L)))
                    .toList();

        } catch (UnauthorizedAccessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("[ADMIN_CATEGORY_DISTRIBUTION_FAILED]", e);
            throw new InternalErrorException("ADMIN_CATEGORY_DISTRIBUTION_FAILED", "카테고리 분포 조회 중 오류가 발생했습니다.");
        }
    }

    private String resolveRootName(Category category) {
        try {
            Category cur = category;
            int guard = 0;

            Set<Long> visited = new HashSet<>();

            while (cur != null && cur.getParent() != null) {
                if (cur.getCategoryId() != null && !visited.add(cur.getCategoryId())) {
                    log.warn("[CATEGORY_PARENT_CYCLE_DETECTED] categoryId={}", cur.getCategoryId());
                    break;
                }
                cur = cur.getParent();
                guard++;
                if (guard > 20) break;
            }
            return cur == null ? null : cur.getCategoryName();

        } catch (Exception e) {
            log.warn("[RESOLVE_ROOT_NAME_FAIL] categoryId={}", (category == null ? null : category.getCategoryId()), e);
            return null;
        }
    }

    // 오늘 거래 금액 조회
    private long calcTodayGmv() {
        LocalDate today = LocalDate.now(KST);
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        try {
            Long sum = bidRepository.sumTodayGmv(start, end);
            return sum == null ? 0L : Math.max(0L, sum);
        } catch (RuntimeException e) {
            log.error("[ADMIN_TODAY_GMV_FAILED] start={} end={}", start, end, e);
            throw new InternalErrorException("ADMIN_TODAY_GMV_FAILED", "오늘 거래 금액 집계 중 오류가 발생했습니다.");
        }
    }

    // 월별 거래 금액 조회
    private long calcMonthlyAvgGmv(int months) {
        if (months <= 0 || months > 24) {
            throw new BadRequestException("MONTHS_INVALID", "months는 1~24 범위여야 합니다.");
        }
        LocalDate firstDayThisMonth = LocalDate.now(KST).withDayOfMonth(1);
        LocalDate fromMonth = firstDayThisMonth.minusMonths(months - 1);

        LocalDateTime start = fromMonth.atStartOfDay();
        LocalDateTime end = firstDayThisMonth.plusMonths(1).atStartOfDay();

        try {
            List<Object[]> rows = bidRepository.sumMonthlyGmv(start, end);

            Map<YearMonth, Long> map = new HashMap<>();
            if (rows != null) {
                for (Object[] r : rows) {
                    if (r == null || r.length < 3) continue;

                    Integer yy = safeInt(r[0]);
                    Integer mm = safeInt(r[1]);
                    Long total = safeLong(r[2]);

                    if (yy == null || mm == null) continue;
                    if (mm < 1 || mm > 12) continue;

                    long v = (total == null ? 0L : Math.max(0L, total));
                    map.put(YearMonth.of(yy, mm), v);
                }
            }

            long sum = 0L;
            for (int i = 0; i < months; i++) {
                YearMonth ym = YearMonth.from(firstDayThisMonth.minusMonths(i));
                sum += map.getOrDefault(ym, 0L);
            }
            return Math.round((double) sum / (double) months);

        } catch (BadRequestException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("[ADMIN_MONTHLY_AVG_GMV_FAILED] months={} start={} end={}", months, start, end, e);
            throw new InternalErrorException("ADMIN_MONTHLY_AVG_GMV_FAILED", "월 평균 거래 금액 집계 중 오류가 발생했습니다.");
        }
    }

    private Integer safeInt(Object object) {
        if (object == null) return null;
        try {
            if (object instanceof Number number) return number.intValue();
            if (object instanceof String string) return Integer.parseInt(string.trim());
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private Long safeLong(Object object) {
        if (object == null) return null;
        try {
            if (object instanceof Number number) return number.longValue();
            if (object instanceof String string) return Long.parseLong(string.trim());
            return null;
        } catch (Exception e) {
            return null;
        }
    }

}
