package com.example.auction.push.controller;

import com.example.auction.common.dto.CommonResDto;
import com.example.auction.push.service.PushService;
import com.example.auction.push.service.TopicService;
import com.google.firebase.messaging.TopicManagementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/push/test")
@RequiredArgsConstructor
public class PushTestController {

    private final PushService pushService;
    private final TopicService topicService;

    // 특정 유저에게 발송
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/user/{userId}")
    public ResponseEntity<CommonResDto> sendToUser(@PathVariable Long userId,
                                                   @RequestParam String title,
                                                   @RequestParam String body) throws Exception {
        int success = pushService.sendToUser(userId, title, body, Map.of("pushId", java.util.UUID.randomUUID().toString()));
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "sendToUser 실행", Map.of("success", success)));
    }

    // 토픽 구독
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/topic/subscribe")
    public ResponseEntity<CommonResDto> subscribe(@RequestBody List<String> tokens,
                                                  @RequestParam String topic) throws Exception {
        TopicManagementResponse res = topicService.subscribeToTopic(tokens, topic);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "subscribe 완료",
                Map.of("success", res.getSuccessCount(), "failure", res.getFailureCount())));
    }

    // 토픽 발송
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/topic/send")
    public ResponseEntity<CommonResDto> sendTopic(@RequestParam String topic,
                                                  @RequestParam String title,
                                                  @RequestParam String body) throws Exception {
        String messageId = topicService.sendToTopic(topic, title, body, Map.of("pushId", java.util.UUID.randomUUID().toString()));
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "topic send OK", Map.of("messageId", messageId)));
    }
}
