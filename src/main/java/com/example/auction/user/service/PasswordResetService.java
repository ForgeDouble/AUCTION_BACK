package com.example.auction.user.service;

import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.BadRequestException;
import com.example.auction.common.exception.InternalErrorException;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.user.domain.PasswordResetToken;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.PasswordResetTokenRepository;
import com.example.auction.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    private static final int TOKEN_EXPIRATION_MINUTES = 15;

    /**
     * 비밀번호 재설정 요청 처리
     */
    public void requestPasswordReset(String email) {
        // 사용자 조회
//        Optional<User> userOpt = userRepository.findByEmailAndDelYn(email, DelYN.N);

        User user = userRepository.findByEmail("user1@auction.test")
                .orElseThrow(() ->  new RuntimeException("User not found"));
//        if (userOpt.isEmpty()) {
//            // 보안을 위해 사용자 존재 여부를 노출하지 않음
//            // 하지만 이메일은 보내지 않음
//            log.warn("[USER_NOT_FOUND] 존재하지 않거나 만료된 계정 email={}", email);
//            return;
//        }

//        User user = userOpt.get();

        // 기존 미사용 토큰 무효화
        tokenRepository.invalidateUserTokens(user.getUserId());

        // 안전한 랜덤 토큰 생성
        String token = generateSecureToken();

        // 토큰 해시화 (DB 저장용)
        String hashedToken = hashToken(token);

        // 토큰 저장
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setToken(hashedToken);
        resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(TOKEN_EXPIRATION_MINUTES));
        tokenRepository.save(resetToken);

        // 이메일 전송 (원본 토큰 사용)
        emailService.sendPasswordResetEmail(email, token);
    }

    /**
     * 비밀번호 재설정 처리
     */
    public void resetPassword(String token, String newPassword) {
        // 토큰 해시화
        String hashedToken = hashToken(token);

        // 토큰 조회
        PasswordResetToken resetToken = tokenRepository.findByToken(hashedToken)
                .orElseThrow(() -> new BadRequestException("INVALID_PS_TOKEN", "유효하지 않거나 만료된 토큰입니다"));

        // 만료 확인
        if (resetToken.isExpired()) {
            log.warn("[TOKEN_EXPIRED] tokenId={}, expiresAt={}",
                    resetToken.getId(), resetToken.getExpiresAt());
            throw new BadRequestException("INVALID_PS_TOKEN",
                    "유효하지 않거나 만료된 토큰입니다");
        }

        // 사용 여부 확인
        if (resetToken.isUsed()) {
            log.warn("[TOKEN_ALREADY_USED] tokenId={}",
                    resetToken.getId());
            throw new BadRequestException("INVALID_PS_TOKEN",
                    "유효하지 않거나 만료된 토큰입니다");
        }

        // 비밀번호 변경
        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // 토큰 사용 처리
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);
        log.info("[PASSWORD_RESET_SUCCESS] userId={}", user.getUserId());
    }

    /**
     * 토큰 검증 처리
     */
    public void validateResetToken(String token) {
        String hashedToken = hashToken(token);

        PasswordResetToken resetToken = tokenRepository.findByToken(hashedToken)
                .orElseThrow(() -> new BadRequestException("INVALID_PS_TOKEN",
                        "유효하지 않거나 만료된 토큰입니다"));

        if (resetToken.isExpired()) {
            throw new BadRequestException("INVALID_PS_TOKEN",
                    "유효하지 않거나 만료된 토큰입니다");
        }

        if (resetToken.isUsed()) {
            throw new BadRequestException("INVALID_PS_TOKEN",
                    "유효하지 않거나 만료된 토큰입니다");
        }
    }

    /**
     * 안전한 랜덤 토큰 생성 (64자)
     */
    private String generateSecureToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return bytesToHex(bytes);
    }

    /**
     * 토큰 해시화 (SHA-256)
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new InternalErrorException("INITIALIZATION_ERROR", "SHA-256 알고리즘 초기화 실패", e);
        }
    }

    /**
     * 바이트 배열을 16진수 문자열로 변환
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
