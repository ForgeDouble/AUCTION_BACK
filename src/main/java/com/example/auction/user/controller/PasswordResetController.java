package com.example.auction.user.controller;

import com.example.auction.common.dto.CommonResDto;
import com.example.auction.user.dto.ForgotPasswordDto;
import com.example.auction.user.dto.ResetPasswordDto;
import com.example.auction.user.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    /**
     * 비밀번호 재설정 요청
     */
    @PostMapping("/forgot_password")
    public ResponseEntity<CommonResDto> forgotPassword(
            @Valid @ModelAttribute ForgotPasswordDto request) {
        log.info("이메일 확인 email={}", request.getEmail());
        passwordResetService.requestPasswordReset(request.getEmail());

        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "비밀번호 재설정 요청 성공", null));
    }

    /**
     * 비밀번호 재설정
     */
    @PostMapping("/reset_password")
    public ResponseEntity<CommonResDto> resetPassword(
            @Valid @ModelAttribute ResetPasswordDto request) {

        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());

        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "비밀번호 재설정 성공", null));

    }
}

