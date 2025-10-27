package com.example.auction.push.service;

import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.google.firebase.messaging.AndroidConfig.Priority.HIGH;

// FCM 전송
@Service
@Slf4j
public class FcmService {
    private final FirebaseMessaging messaging;
    public FcmService(FirebaseMessaging messaging) { this.messaging = messaging; }

    public String sendToToken(String token, String title, String body, Map<String, String> data) throws Exception {

        var webPush = WebpushConfig.builder()
                .setNotification(WebpushNotification.builder()
                        .setTitle(title)
                        .setBody(body)
                        // .setIcon("https://.../icon.png")
                        // .setClickAction("/notifications")
                        .build())
                .putAllData(data != null ? data : Map.of())
                .putHeader("TTL", "3600")
                .build();

        Message msg = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data != null ? data : Map.of())
                .setWebpushConfig(webPush)
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(HIGH)
                        .setTtl(Duration.ofHours(1).toMillis())
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .putHeader("apns-priority", "10")
                        .setAps(Aps.builder().setContentAvailable(true).build())
                        .build())
                .build();
        String messageId = messaging.send(msg);
        log.debug("[FCM] messageId 보낸 값 : {}", messageId);
        return messageId;
    }

    public BatchResponse sendMulticast(List<String> tokens, String title, String body, Map<String, String> data) throws Exception {
        var webpush = WebpushConfig.builder()
                .setNotification(WebpushNotification.builder().setTitle(title).setBody(body).build())
                .putAllData(data != null ? data : Map.of())
                .putHeader("TTL", "3600")
                .build();

        log.info("[FCM] 멀티케스트 사이즈 : {}", tokens.size());
        MulticastMessage msg = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data != null ? data : Map.of())
                .setWebpushConfig(webpush)
                .setAndroidConfig(AndroidConfig.builder().setPriority(HIGH).setTtl(Duration.ofHours(1).toMillis()).build())
                .setApnsConfig(ApnsConfig.builder().putHeader("apns-priority","10").build())
                .build();
        BatchResponse batchResponse = messaging.sendMulticast(msg);
        log.info("[FCM] multicast result success={}, failure={}", batchResponse.getSuccessCount(), batchResponse.getFailureCount());
        return batchResponse;
    }

    // 재시도 관련 코드 -> 일시 오류 일 경우 지수 백 오프 알고리즘 실행
    public BatchResponse sendMulticastWithRetry(List<String> tokens, String title, String body, Map<String, String> data) throws Exception {
        int attempts = 0;
        while (true) {
            try {
                return sendMulticast(tokens, title, body, data);
            } catch (FirebaseMessagingException firebaseMessagingException) {
                var code = firebaseMessagingException.getMessagingErrorCode();
                if (code == MessagingErrorCode.UNAVAILABLE || code == MessagingErrorCode.INTERNAL) {
                    attempts++;
                    long backoff = 200L * attempts * attempts;
                    log.warn("[FCM] 일시적 오류 ({}), 재시도 #{} 백오프 {}ms", code, attempts, backoff);
                    if (attempts <= 3) { Thread.sleep(backoff); continue; }
                }
                log.error("[FCM] 전송에 실패했습니다 : code={}, msg={}", firebaseMessagingException.getMessagingErrorCode(), firebaseMessagingException.getMessage());
                throw firebaseMessagingException;
            }
        }
    }
}
