package com.example.auction.admin.service;

import com.example.auction.admin.dto.AdminCategoryDistributionDto;
import com.example.auction.admin.dto.AdminOverviewResponse;
import com.example.auction.bid.domain.IsWinned;
import com.example.auction.bid.repository.BidRepository;
import com.example.auction.category.domain.Category;
import com.example.auction.category.repository.CategoryRepository;
import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.common.exception.UnauthorizedAccessException;
import com.example.auction.product.domain.Status;
import com.example.auction.product.repository.CategoryCountRow;
import com.example.auction.product.repository.ProductRepository;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import com.example.auction.user.service.UserStatusService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
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

    private void ensureAdmin() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException("로그인중인 User"));
        if (user.getAuthority() != Authority.ADMIN && user.getAuthority() != Authority.INQUIRY) {
            throw new UnauthorizedAccessException("관리자 외 권한이 없습니다.");
        }
    }

    public AdminOverviewResponse getOverview() {
        LocalDate today = LocalDate.now(KST);
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        long todayNewUsers = userRepository.countByCreatedAtBetween(start, end);
        long todayCreatedAuctions = productRepository.countByCreatedAtBetween(start, end);

        long todaySold = productRepository.countByStatusAndUpdatedAtBetween(Status.SELLED, start, end);
        long todayNotSold = productRepository.countByStatusAndUpdatedAtBetween(Status.NOTSELLED, start, end);
        long todayEndedAuctions = todaySold + todayNotSold;

        long ongoingAuctions = productRepository.countByStatusAndBlockedFalse(Status.PROCESSING);
        long totalBids = bidRepository.count();

        long realtimeUsers = userStatusService.getRealtimeUsersCount();
        long todayActiveUsers = userStatusService.getDailyActiveCount(today);

        long reportsOpen = adminReportCounter.getOpenReportsCount();

//        long todayTradeAmount = bidRepository.sumWinningAmountForSoldProductsBetween(
//                IsWinned.Y, Status.SELLED, start, end
//        );
//
//        // 최근 6개월 월 평균 거래금액(낙찰 합 기준)
//        long monthlyAvgTradeAmount = calcMonthlyAvg6();
        long todayTradeAmount = calcTodayGmv();
        long monthlyAvgTradeAmount = calcMonthlyAvgGmv(6);

        List<AdminOverviewResponse.HourlyPoint> hourly = new ArrayList<>();
        for (UserStatusService.HourlyPoint p : userStatusService.getHourlySeries(today)) {
            hourly.add(new AdminOverviewResponse.HourlyPoint(p.hour(), p.users()));
        }

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

        List<CategoryCountRow> rows = productRepository.countByCategoryIdExcludingDeletedBlocked();

        if (rows == null || rows.isEmpty()) {
            return TOP_LEVEL.stream()
                    .map(name -> new AdminCategoryDistributionDto(name, 0L))
                    .toList();
        }

        // leafCategoryId -> count
        Map<Long, Long> leafCounts = new HashMap<>();
        for (CategoryCountRow r : rows) {
            if (r.getCategoryId() == null) continue;
            leafCounts.put(r.getCategoryId(), r.getCnt() == null ? 0L : r.getCnt());
        }

        if (leafCounts.isEmpty()) {
            return TOP_LEVEL.stream()
                    .map(name -> new AdminCategoryDistributionDto(name, 0L))
                    .toList();
        }

        Map<Long, Category> leafMap = categoryRepository.findAllById(leafCounts.keySet())
                .stream()
                .collect(Collectors.toMap(Category::getCategoryId, c -> c));

        // rootName(대분류명) 기준 합산
        Map<String, Long> rootSum = new HashMap<>();
        for (Map.Entry<Long, Long> e : leafCounts.entrySet()) {
            Category leaf = leafMap.get(e.getKey());
            if (leaf == null) continue;

            String rootName = resolveRootName(leaf);
            rootSum.merge(rootName, e.getValue(), Long::sum);
        }

        return TOP_LEVEL.stream()
                .map(name -> new AdminCategoryDistributionDto(name, rootSum.getOrDefault(name, 0L)))
                .toList();
    }

    private String resolveRootName(Category c) {
        Category cur = c;
        int guard = 0;
        while (cur.getParent() != null) {
            cur = cur.getParent();
            guard++;
            if (guard > 10) break;
        }
        return cur.getCategoryName();
    }

    // 오늘 거래 금액 조회
    private long calcTodayGmv() {
        LocalDate today = LocalDate.now(KST);
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        Long sum = bidRepository.sumTodayGmv(start, end);
        return sum == null ? 0L : sum;
    }

    // 월별 거래 금액 조회
    private long calcMonthlyAvgGmv(int months) {
        LocalDate firstDayThisMonth = LocalDate.now(KST).withDayOfMonth(1);
        LocalDate fromMonth = firstDayThisMonth.minusMonths(months - 1);

        LocalDateTime start = fromMonth.atStartOfDay();
        LocalDateTime end = firstDayThisMonth.plusMonths(1).atStartOfDay();

        List<Object[]> rows = bidRepository.sumMonthlyGmv(start, end);

        Map<YearMonth, Long> map = new HashMap<>();
        for (Object[] r : rows) {
            int yy = ((Number) r[0]).intValue();
            int mm = ((Number) r[1]).intValue();
            long total = ((Number) r[2]).longValue();
            map.put(YearMonth.of(yy, mm), total);
        }

        long sum = 0L;
        for (int i = 0; i < months; i++) {
            YearMonth ym = YearMonth.from(firstDayThisMonth.minusMonths(i));
            sum += map.getOrDefault(ym, 0L);
        }
        return Math.round((double) sum / months);
    }

}
