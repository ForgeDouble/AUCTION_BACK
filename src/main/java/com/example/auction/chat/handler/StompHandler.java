package com.example.auction.chat.handler;

import com.example.auction.common.auth.JwtTokenProvider;
import com.example.auction.common.service.CustomTokenExpiredStrategy;
import com.example.auction.user.service.UserStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class StompHandler implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomTokenExpiredStrategy customTokenExpiredStrategy;
    private final UserStatusService userStatusService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }

        // 엔드포인트 타입 확인
        String endpointType = (String) accessor.getSessionAttributes().get("endpointType");
        boolean isPublic = "public".equals(endpointType);

        // CONNECT 시 인증 처리
        if (StompCommand.CONNECT.equals(command)) {
            if (isPublic) {
                log.info("✅ 공개 엔드포인트 연결 허용 (인증 없음)");
                return message;
            }

            // 인증 필수 로직
            String raw = firstNonNull(
                    accessor.getFirstNativeHeader("Authorization"),
                    accessor.getFirstNativeHeader("authorization"),
                    accessor.getFirstNativeHeader("token")
            );
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("인증 헤더가 없습니다.");
            }

            String token = raw.startsWith("Bearer ")
                    ? raw.substring(7).trim()
                    : raw.trim();

            // 토큰 형식/서명 검증
            if (!jwtTokenProvider.validateToken(token)) {
                throw new IllegalArgumentException("유효하지 않은 JWT 토큰입니다.");
            }

            String email = jwtTokenProvider.getEmailFromToken(token);

            // 단일 세션(로그인 Redis) 체크
            String current = customTokenExpiredStrategy.get(email);
            if (current == null || !current.equals(token)) {
                throw new IllegalArgumentException("다른 기기에서 로그인했거나 토큰이 무효화되었습니다.");
            }

            // presence 기록하기
            userStatusService.touch(email);
            // 세션에 email 저장해두기
            accessor.getSessionAttributes().put("email", email);
        }

        // SUBSCRIBE / SEND 에서는 세션에 저장된 email 로만 presence 갱신
        if (StompCommand.SUBSCRIBE.equals(command)
                || StompCommand.SEND.equals(command)) {

            if (isPublic) {
                // 공개 엔드포인트는 인증 체크 건너뛰기
                return message;
            }

            Object emailObj = accessor.getSessionAttributes().get("email");
            if (emailObj instanceof String email && !email.isBlank()) {
                userStatusService.touch(email);
            }
        }

        return message;
    }

    private String firstNonNull(String... xs) {
        for (String x : xs) if (x != null) return x;
        return null;
    }
}
