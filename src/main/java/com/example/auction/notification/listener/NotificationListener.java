package com.example.auction.notification.listener;


import com.example.auction.notification.event.UserTemporarilyRestrictedEvent;
import com.example.auction.push.service.PushService;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final PushService pushService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserTemporarilyRestricted(UserTemporarilyRestrictedEvent e) {
        try {
            var data = Map.of(
                    "type", "USER_VIEW_ONLY",
                    "category", e.category().name(),
                    "pendingCount", String.valueOf(e.categoryPendingCount())
            );
            pushService.sendToUser(
                    e.targetUserId(),
                    "임시 제한이 적용되었습니다",
                    (e.reason() == null || e.reason().isBlank())
                            ? "신고 누적으로 임시 제한 상태가 되었습니다."
                            : e.reason(),
                    data
            );
        } catch (Exception ex) {
            LoggerFactory.getLogger(getClass())
                    .warn("[알림] UserTemporarilyRestrictedEvent push 실패 userId={}", e.targetUserId(), ex);
        }
    }
}