package com.example.auction.chat.handler;

import com.example.auction.common.auth.JwtTokenProvider;
import com.example.auction.common.service.CustomTokenExpiredStrategy;
import com.example.auction.user.service.UserStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
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
                log.info("공개 엔드포인트 연결 허용 (인증 없음)");
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

        // [추가] SEND 시 토큰 검증
        if (StompCommand.SEND.equals(command)) {
            String destination = accessor.getDestination();

            log.info("SEND destination: {}", destination);

            // /app/bid 같은 민감한 액션은 인증 필수
            if (destination != null && requiresAuth(destination)) {
                String raw = firstNonNull(
                        accessor.getFirstNativeHeader("Authorization"),
                        accessor.getFirstNativeHeader("authorization"),
                        accessor.getFirstNativeHeader("token")
                );

                log.info("Authorization header: {}", raw);
                if (raw == null || raw.isBlank()) {
                    throw new IllegalArgumentException("인증이 필요한 작업입니다.");
                }

                String token = raw.startsWith("Bearer ")
                        ? raw.substring(7).trim()
                        : raw.trim();

                if (!jwtTokenProvider.validateToken(token)) {
                    throw new IllegalArgumentException("유효하지 않은 JWT 토큰입니다.");
                }

                String email = jwtTokenProvider.getEmailFromToken(token);
                String current = customTokenExpiredStrategy.get(email);
                if (current == null || !current.equals(token)) {
                    throw new IllegalArgumentException("다른 기기에서 로그인했거나 토큰이 무효화되었습니다.");
                }

                // 메시지 헤더에 email 추가 (컨트롤러에서 사용 가능)
                // setUser 후 메시지 재생성
                accessor.setUser(() -> email);
                accessor.setLeaveMutable(true);  // mutable 상태 유지

                log.info("입찰 요청 인증 완료: {}", email);

                // 수정된 accessor로 메시지 재생성
                return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
            }

            // 공개 엔드포인트의 일반 메시지는 인증 없이 통과
            if (isPublic && !requiresAuth(destination)) {
                return message;
            }

            // 비공개 엔드포인트는 세션 이메일로 presence 갱신
            if (!isPublic) {
                Object emailObj = accessor.getSessionAttributes().get("email");
                if (emailObj instanceof String email && !email.isBlank()) {
                    userStatusService.touch(email);
                }
            }
        }

        // SUBSCRIBE 처리 - SEND와 분리
        if (StompCommand.SUBSCRIBE.equals(command)) {
            if (isPublic) {
                return message;
            }

            Object emailObj = accessor.getSessionAttributes().get("email");
            if (emailObj instanceof String email && !email.isBlank()) {
                userStatusService.touch(email);
            }
        }

        return message;
    }

    // 인증이 필요한 destination 판별 메서드
    private boolean requiresAuth(String destination) {
        if (destination == null) return false;

        return destination.equals("/app/bid");
    }

    private String firstNonNull(String... xs) {
        for (String x : xs) if (x != null) return x;
        return null;
    }
}
