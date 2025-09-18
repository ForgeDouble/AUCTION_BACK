package com.example.auction.push.controller;

import com.example.auction.push.service.FcmService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/* 로컬환경 연결·환경 점검 컨트롤러 */
@Profile("local")
@RestController
@RequestMapping("/dev/fcm")
@RequiredArgsConstructor
public class FcmDevController {
    private final FcmService fcmService;

    @PostMapping("/send-to-token")
    public ResponseEntity<?> sendToToken(
            @RequestParam String token,
            @RequestParam(defaultValue = "Local Test") String title,
            @RequestParam(defaultValue = "Hello from Spring") String body
    ) {
        try {
            String messageId = fcmService.sendToToken(token, title, body, Map.of("source","local"));
            return ResponseEntity.ok(Map.of("messageId", messageId));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
