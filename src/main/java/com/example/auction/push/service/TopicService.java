package com.example.auction.push.service;

import com.example.auction.push.domain.DeviceToken;
import com.example.auction.push.repository.DeviceTokenRepository;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TopicService {

    private final FirebaseMessaging messaging;

    public TopicManagementResponse subscribeToTopic(List<String> tokens, String topic) throws FirebaseMessagingException {
        if (tokens == null || tokens.isEmpty()) {
            throw new IllegalArgumentException("구독할 토큰 목록이 비어 있습니다.");
        }
        TopicManagementResponse topicManagementResponse = messaging.subscribeToTopic(tokens, topic);
        log.info("[FCM] 구독 토픽 = {}, 성공 = {}, 실패 = {}", topic, topicManagementResponse.getSuccessCount(), topicManagementResponse.getFailureCount());
        return topicManagementResponse;
    }

    public TopicManagementResponse unsubscribeFromTopic(List<String> tokens, String topic) throws FirebaseMessagingException {
        if (tokens == null || tokens.isEmpty()) {
            throw new IllegalArgumentException("해지할 토큰 목록이 비어 있습니다.");
        }
        TopicManagementResponse res = messaging.unsubscribeFromTopic(tokens, topic);
        log.info("[FCM] 해지 토픽 = {}, 성공 = {}, 실패 = {}", topic, res.getSuccessCount(), res.getFailureCount());
        return res;
    }

    // 토픽 발송
    public String sendToTopic(String topic, String title, String body, Map<String, String> data) throws FirebaseMessagingException {
        Message msg = Message.builder()
                .setTopic(topic)
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

        String messageId = messaging.send(msg);
        log.info("[FCM] 토픽 전송 = {}, messageId = {}", topic, messageId);
        return messageId;
    }
}
