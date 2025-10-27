package com.example.auction.push.service;

import com.example.auction.common.crypto.TokenHash;
import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.push.domain.DevicePlatform;
import com.example.auction.push.domain.DeviceToken;
import com.example.auction.push.dto.TokenRegisterDto;
import com.example.auction.push.repository.DeviceTokenRepository;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/*
* 해당 코드를 통해서 등록 / 해제 / 유저 발송 / 죽은 토큰 정리 의 기능 구현
*
* */
@Service
@Slf4j
@RequiredArgsConstructor
public class PushService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final UserRepository userRepository;
    private final FcmService fcmService;

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException("현재 로그인한 유저 정보를 찾을 수 없습니다."));
    }

    @Transactional
    public void registerToken(TokenRegisterDto dto) {
        if (dto.getToken() == null || dto.getToken().isBlank()) {
            throw new IllegalArgumentException("FCM 토큰이 필요합니다.");
        }
        User user = currentUser();
        String hash = TokenHash.hmacSha256B64(dto.getToken());
        String hashPrefix = hash.substring(0, 8);

        DeviceToken existing = deviceTokenRepository.findByTokenHash(hash).orElse(null);
        DeviceToken token;
        if (existing != null) {
            Long prevUserId = existing.getUser() != null ? existing.getUser().getUserId() : null;
            if (prevUserId != null && !prevUserId.equals(user.getUserId())) {
                log.warn("[Push] 토큰 전송 감지 hashPrefix={} fromUser={} -> toUser={}",
                        hashPrefix, prevUserId, user.getUserId());
            }
            token = existing;
        } else {
            token = DeviceToken.builder().build();
            log.info("[Push] 토큰 생성 hashPrefix={} userId={}", hashPrefix, user.getUserId());
        }

        token.setUser(user);
        token.setToken(dto.getToken());
        token.setTokenHash(hash);
        token.setPlatform(dto.getPlatform() == null ? DevicePlatform.WEB : dto.getPlatform());
        token.setAppVersion(dto.getAppVersion());
        token.setDeviceModel(dto.getDeviceModel());
        token.setValid(true);
        deviceTokenRepository.save(token);

        log.info("[Push] 토큰 저장값 hashPrefix={} userId={} platform={} valid={}",
                hashPrefix, user.getUserId(), token.getPlatform(), token.isValid());
    }

    @Transactional
    public void unregisterToken(String tokenPlain) {
        if (tokenPlain == null || tokenPlain.isBlank()) return;
        String hash = TokenHash.hmacSha256B64(tokenPlain);
        String hashPrefix = hash.substring(0, 8);
        deviceTokenRepository.deleteByTokenHash(hash);
        log.info("[Push] 저장되지 않은 tokenHashPrefix={}", hashPrefix);
    }

    /* 특정 유저의 모든 유효 토큰으로 전송 (실패 토큰 정리) */
    @Transactional
    public int sendToUser(Long userId, String title, String body, Map<String,String> data) throws Exception {

        if (userId == null) {
            log.warn("[Push] userId 이 없습니다. 알림 전송 스킵. title={}, data={}", title, data);
            return 0;
        }
        User target = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User"));
        List<DeviceToken> tokens = deviceTokenRepository.findAllByUser_UserIdAndValidTrue(target.getUserId());
        if (tokens.isEmpty()) {
            log.info("[Push] userId={} 에 토큰이 없습니다", userId);
            return 0;
        }
        List<String> validTokens = tokens.stream().map(DeviceToken::getToken).distinct().toList();
        log.info("[Push] sendToUser userId={}, tokenCount={}", userId, validTokens.size());

        int success = 0;
        for (int i = 0; i < validTokens.size(); i += 500) {
            List<String> chunk = validTokens.subList(i, Math.min(i + 500, validTokens.size()));
            BatchResponse batchResponse = fcmService.sendMulticastWithRetry(chunk, title, body, data);

            // 실패 시 토큰 정리 코드
            for (int idx = 0; idx < batchResponse.getResponses().size(); idx++) {
                var r = batchResponse.getResponses().get(idx);
                if (!r.isSuccessful()) {
                    FirebaseMessagingException fme = r.getException();
                    if (fme != null) {
                        // 비정상 토큰 정리
                        MessagingErrorCode code = fme.getMessagingErrorCode();
                        if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                            deviceTokenRepository.deleteByTokenHash(TokenHash.hmacSha256B64(chunk.get(idx)));
                            log.info("[Push] 비성장적으로 죽은 토큰입니다 (code={})", code);
                        } else {
                            log.warn("[Push] 토큰 발송이 실패했습니다 code={}", code);
                        }
                    }
                }
            }
            success += batchResponse.getSuccessCount();
        }
        log.info("[Push] 발송받은 userId={}, 성공 = {}", userId, success);
        return success;
    }
}

