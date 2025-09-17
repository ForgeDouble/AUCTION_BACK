package com.example.auction.push.service;

import com.google.firebase.messaging.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

// FCM 전송
@Service
public class FcmService {
    private final FirebaseMessaging messaging;
    public FcmService(FirebaseMessaging messaging) { this.messaging = messaging; }

    public String sendToToken(String token, String title, String body, Map<String, String> data) throws Exception {
        Message msg = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data != null ? data : Map.of())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setTtl(Duration.ofHours(1).toMillis())
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .putHeader("apns-priority", "10")
                        .setAps(Aps.builder().setContentAvailable(true).build())
                        .build())
                .build();
        return messaging.send(msg);
    }

    public BatchResponse sendMulticast(List<String> tokens, String title, String body, Map<String, String> data) throws Exception {

        MulticastMessage msg = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data != null ? data : Map.of())
                .build();
        return messaging.sendMulticast(msg);
    }

    public BatchResponse sendMulticastWithRetry(List<String> tokens, String title, String body, Map<String, String> data) throws Exception {
        int attempts = 0;
        while (true) {
            try {
                return sendMulticast(tokens, title, body, data);
            } catch (FirebaseMessagingException e) {
                MessagingErrorCode code = e.getMessagingErrorCode();
                if (code == MessagingErrorCode.UNAVAILABLE || code == MessagingErrorCode.INTERNAL) {
                    if (++attempts <= 3) {
                        Thread.sleep(200L * attempts * attempts);
                        continue;
                    }
                }
                throw e;
            }
        }
    }
}
