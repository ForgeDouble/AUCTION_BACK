package com.example.auction.admin.metrics;

import com.example.auction.admin.service.OngoingAuctionMetricsService;
import com.example.auction.user.service.UserStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AdminRealtimeBroadcaster {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final SimpMessagingTemplate messagingTemplate;
    private final UserStatusService userStatusService;
    private final OngoingAuctionMetricsService ongoingAuctionMetricsService;

    @Scheduled(fixedDelay = 2000)
    public void pushRealtime() {

        long realtimeUsers = userStatusService.getRealtimeUsersCount();
        long todayActiveUsers = userStatusService.getDailyActiveCount(LocalDate.now(KST));
        long ongoingAuctions = ongoingAuctionMetricsService.getOngoingAuctionsCached();

        Map<String, Object> payload = new HashMap<>();
        payload.put("realtimeUsers", realtimeUsers);
        payload.put("todayActiveUsers", todayActiveUsers);
        payload.put("ongoingAuctions", ongoingAuctions);
        payload.put("ts", System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/admin/realtime", payload);
    }
}
