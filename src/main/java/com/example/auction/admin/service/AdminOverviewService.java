package com.example.auction.admin.service;

import com.example.auction.admin.dto.AdminOverviewResponse;
import com.example.auction.bid.domain.IsWinned;
import com.example.auction.bid.repository.BidRepository;
import com.example.auction.product.domain.Status;
import com.example.auction.product.repository.ProductRepository;
import com.example.auction.user.repository.UserRepository;
import com.example.auction.user.service.UserStatusService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
public class AdminOverviewService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final BidRepository bidRepository;
    private final UserStatusService userStatusService;
    private final AdminReportCounter adminReportCounter;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public AdminOverviewService(UserRepository userRepository, ProductRepository productRepository, BidRepository bidRepository, UserStatusService userStatusService, AdminReportCounter adminReportCounter) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.bidRepository = bidRepository;
        this.userStatusService = userStatusService;
        this.adminReportCounter = adminReportCounter;
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

        long todayTradeAmount = bidRepository.sumWinningAmountForSoldProductsBetween(
                IsWinned.Y, Status.SELLED, start, end
        );

        // 최근 6개월 월 평균 거래금액(낙찰 합 기준)
        long monthlyAvgTradeAmount = calcMonthlyAvg6();

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
}
