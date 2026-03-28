package com.example.auction.admin.service;


import com.example.auction.admin.dto.AdminOverviewResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminRealtimePublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final AdminOverviewService adminOverviewService;

    @Scheduled(fixedDelay = 15000)
    public void pushOverview() {
        try {
            AdminOverviewResponse dto = adminOverviewService.getOverview();
            messagingTemplate.convertAndSend("/topic/admin/overview", dto);
        } catch (Exception e) {
//            log.warn("admin overview push failed: {}", e.getMessage());
        }
    }
}
