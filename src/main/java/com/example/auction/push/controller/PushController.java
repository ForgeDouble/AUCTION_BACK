package com.example.auction.push.controller;

import com.example.auction.common.dto.CommonResDto;
import com.example.auction.push.dto.TokenRegisterDto;
import com.example.auction.push.service.PushService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/* 실 API */
@RestController
@RequestMapping("/push")
@RequiredArgsConstructor
public class PushController {
    private final PushService pushService;

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/register")
    public ResponseEntity<CommonResDto> register(@RequestBody TokenRegisterDto dto) {
        pushService.registerToken(dto);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "FCM 토큰 등록 성공", null));
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/unregister")
    public ResponseEntity<CommonResDto> unregister(@RequestParam String token) {
        pushService.unregisterToken(token);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "FCM 토큰 해제 성공", null));
    }
}