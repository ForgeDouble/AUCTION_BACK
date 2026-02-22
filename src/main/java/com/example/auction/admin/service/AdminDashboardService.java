package com.example.auction.admin.service;

import com.example.auction.admin.dto.AdminDashboardDto;
import com.example.auction.bid.domain.IsWinned;
import com.example.auction.bid.repository.BidRepository;
import com.example.auction.common.auth.SecurityUserContext;
import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.BadRequestException;
import com.example.auction.common.exception.InternalErrorException;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.common.exception.UnauthorizedAccessException;
import com.example.auction.product.domain.Status;
import com.example.auction.product.repository.ProductRepository;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import com.example.auction.user.service.UserStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class AdminDashboardService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final BidRepository bidRepository;

    private final UserStatusService userStatusService;

    public AdminDashboardService(UserRepository userRepository, ProductRepository productRepository, BidRepository bidRepository, UserStatusService userStatusService) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.bidRepository = bidRepository;
        this.userStatusService = userStatusService;
    }
    public AdminDashboardDto getDashboard() {
        ensureAdmin();
        try {
            LocalDate today = LocalDate.now(KST);
            LocalDateTime start = today.atStartOfDay();
            LocalDateTime end = start.plusDays(1);

            long todayNewUsers = userRepository.countByCreatedAtBetween(start, end);
            long todayNewAuctions = productRepository.countByCreatedAtBetween(start, end);

            long todaySold = productRepository.countByStatusAndUpdatedAtBetween(Status.SELLED, start, end);
            long todayNotSold = productRepository.countByStatusAndUpdatedAtBetween(Status.NOTSELLED, start, end);
            long todayEndedAuctions = todaySold + todayNotSold;

            long totalBids = bidRepository.count();
            long ongoingAuctions = productRepository.countByStatusAndBlockedFalse(Status.PROCESSING);

            long realtimeUsers = userStatusService.getRealtimeUsersCount();
            long todayActiveUsers = userStatusService.getDailyActiveCount(today);

            long todayTotalAmount = bidRepository.sumWinningAmountForSoldProductsBetween(
                    IsWinned.Y, Status.SELLED, start, end
            );

            long monthlyAvg = 0;

            // 신고 카운트 연결 지점
            long reports = 0;

            // 시간대 선형 차트 데이터
            List<UserStatusService.HourlyPoint> hourly = userStatusService.getHourlySeries(today);
            List<AdminDashboardDto.HourlyPoint> hourlyDto = new ArrayList<>();
            for (UserStatusService.HourlyPoint p : hourly) {
                hourlyDto.add(new AdminDashboardDto.HourlyPoint(String.format("%02d:00", p.hour()), p.users()));
            }

            var p = SecurityUserContext.principal();
            var admin = new AdminDashboardDto.AdminProfile(
                    p.getEmail(),
                    (p.getNickname() == null || p.getNickname().isBlank()) ? "관리자" : p.getNickname(),
                    (p.getAuthority() == null) ? "ADMIN" : p.getAuthority().name()
            );

            var stats = new AdminDashboardDto.OverviewStats(
                    todayNewUsers, todayNewAuctions, todayEndedAuctions,
                    totalBids, ongoingAuctions,
                    reports, realtimeUsers, todayActiveUsers
            );

            var money = new AdminDashboardDto.MoneyStats(todayTotalAmount, monthlyAvg);

            return new AdminDashboardDto(admin, stats, hourlyDto, money);
        } catch (BadRequestException | ResourceNotFoundException | UnauthorizedAccessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InternalErrorException("ADMIN_DASHBOARD_FAILED", "대시보드 조회 중 오류가 발생했습니다.");
        }
    }

//    private void checkAdmin() {
//        var p = SecurityUserContext.principal();
//        if (p.getAuthority() == null || p.getAuthority() != Authority.ADMIN && ) {
//            throw new IllegalStateException("ADMIN 권한이 필요합니다.");
//        }
//    }
    private void ensureAdmin() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException("로그인중인 User"));
        if (user.getAuthority() != Authority.ADMIN && user.getAuthority() != Authority.INQUIRY) {
            throw new UnauthorizedAccessException("관리자 외 권한이 없습니다.");
        }
    }


}
