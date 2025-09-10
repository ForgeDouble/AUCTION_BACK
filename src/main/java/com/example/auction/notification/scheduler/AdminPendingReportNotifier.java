package com.example.auction.notification.scheduler;

import com.example.auction.common.domain.DelYN;
import com.example.auction.push.service.PushService;
import com.example.auction.report.domain.ReportStatus;
import com.example.auction.report.repository.ReportRepository;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/* 관리자가 이벤트를 열람하지 않은 것을 조회하여 스케쥴러로 알림 올 수 있게 구성 */
@Component
@RequiredArgsConstructor
public class AdminPendingReportNotifier {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final PushService pushService;

    private final AtomicLong lastSentEpochMs = new AtomicLong(0);
    private static final long COOLDOWN_MS = 30 * 60 * 1000L; // 30분

    // 10분마다 확인 (초기 지연 90초)
    @Scheduled(fixedDelay = 10 * 60 * 1000L, initialDelay = 90 * 1000L)
    public void notifyAdminsIfPending() {
        long pending = reportRepository.countByStatus(ReportStatus.PENDING);
        if (pending <= 0) return;

        long now = Instant.now().toEpochMilli();
        long last = lastSentEpochMs.get();

        if (now - last < COOLDOWN_MS) return;

        List<User> admins = userRepository.findAllByAuthorityAndDelYn(Authority.ADMIN, DelYN.N);
        for (User admin : admins) {
            try {
                pushService.sendToUser(
                        admin.getUserId(),
                        "미처리 신고 " + pending + "건",
                        "관리자 페이지에서 신고를 확인·처리해 주세요.",
                        Map.of("type", "ADMIN_PENDING_REPORTS", "count", String.valueOf(pending))
                );
            } catch (Exception ignored) { }
        }
        lastSentEpochMs.set(now);
    }
}
