package com.example.auction.push.service;

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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
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

        DeviceToken token = deviceTokenRepository.findByToken(dto.getToken())
                .orElse(DeviceToken.builder().token(dto.getToken()).build());
        token.setUser(user);
        token.setPlatform(dto.getPlatform() == null ? DevicePlatform.WEB : dto.getPlatform());
        token.setAppVersion(dto.getAppVersion());
        token.setDeviceModel(dto.getDeviceModel());
        token.setValid(true);
        deviceTokenRepository.save(token);
    }

    @Transactional
    public void unregisterToken(String token) {
        if (token == null || token.isBlank()) return;
        deviceTokenRepository.deleteByToken(token);
    }

    /* 특정 유저의 모든 유효 토큰으로 전송(실패 토큰 정리 포함) */
    @Transactional
    public int sendToUser(Long userId, String title, String body, Map<String,String> data) throws Exception {
        User target = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User"));
        List<DeviceToken> tokens = deviceTokenRepository.findAllByUser(target);
        if (tokens.isEmpty()) return 0;

        List<String> validTokens = tokens.stream().filter(DeviceToken::isValid).map(DeviceToken::getToken).toList();
        if (validTokens.isEmpty()) return 0;

        int success = 0;
        for (int i = 0; i < validTokens.size(); i += 500) {
            List<String> chunk = validTokens.subList(i, Math.min(i + 500, validTokens.size()));
            BatchResponse batchResponse = fcmService.sendMulticast(chunk, title, body, data);

            for (int idx = 0; idx < batchResponse.getResponses().size(); idx++) {
                var r = batchResponse.getResponses().get(idx);
                if (!r.isSuccessful()) {
                    FirebaseMessagingException fme = r.getException();
                    if (fme != null) {
                        MessagingErrorCode code = fme.getMessagingErrorCode();
                        if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                            deviceTokenRepository.deleteByToken(chunk.get(idx));
                        }
                    }
                }
            }
            success += batchResponse.getSuccessCount();
        }
        return success;
    }
}

