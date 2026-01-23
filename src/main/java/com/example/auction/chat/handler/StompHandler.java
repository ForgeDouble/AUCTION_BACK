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
//        Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
//        String endpointType = sessionAttrs != null ? (String) sessionAttrs.get("endpointType") : null;

        boolean isPublic = "public".equals(endpointType);
//        boolean isAdmin = "admin".equals(endpointType);
        boolean isAdminEndpoint = "admin".equals(endpointType);

        // CONNECT 시 인증 처리
        if (StompCommand.CONNECT.equals(command)) {
            if (isPublic) {
                log.info("공개 엔드포인트 연결 허용 (인증 없음)");
                return message;
            }
            // admin 이랑 , 채팅 관련 private
//            String raw = firstNonNull(
//                    accessor.getFirstNativeHeader("Authorization"),
//                    accessor.getFirstNativeHeader("authorization"),
//                    accessor.getFirstNativeHeader("token")
//            );
//            if (raw == null || raw.isBlank()) {
//                throw new IllegalArgumentException("인증 헤더가 없습니다.");
//            }
//
//            String token = raw.startsWith("Bearer ")
//                    ? raw.substring(7).trim()
//                    : raw.trim();
//
//            // 토큰 형식/서명 검증
//            if (!jwtTokenProvider.validateToken(token)) {
//                throw new IllegalArgumentException("유효하지 않은 JWT 토큰입니다.");
//            }
            String token = extractToken(accessor);
            if (!jwtTokenProvider.validateToken(token)) {
                throw new IllegalArgumentException("유효하지 않은 JWT 토큰입니다.");
            }

            String email = jwtTokenProvider.getEmailFromToken(token);

            String current = customTokenExpiredStrategy.get(email);
            if (current == null || !current.equals(token)) {
                throw new IllegalArgumentException("다른 기기에서 로그인했거나 토큰이 무효화되었습니다.");
            }

            if (isAdminEndpoint) {
                String auth = jwtTokenProvider.getAuthorityFromToken(token); // "ADMIN"/"INQUIRY"/"USER"
                String upper = auth == null ? "" : auth.trim().toUpperCase();

                if (!(upper.contains("ADMIN") || upper.contains("INQUIRY"))) {
                    throw new IllegalArgumentException("관리자 소켓에 접근 권한이 없습니다.");
                }
            }

            // presence 기록하기
            userStatusService.touch(email);
            // 세션에 email 저장
            accessor.getSessionAttributes().put("email", email);
            accessor.setUser(() -> email);

            return message;
        }

        // SEND 처리
        if (StompCommand.SEND.equals(command)) {
            String destination = accessor.getDestination();

            // 공개 엔드포인트면 인증 없는 SEND 허용
            if (isPublic && !requiresAuth(destination)) return message;

            // private/admin 는 세션 email로 presence 갱신
            Object emailObj = accessor.getSessionAttributes().get("email");
            if (emailObj instanceof String email && !email.isBlank()) {
                userStatusService.touch(email);
            }

            // 기존: /app/bid 는 헤더 토큰 인증 필수
            if (destination != null && requiresAuth(destination)) {
                String token = extractToken(accessor);

                if (!jwtTokenProvider.validateToken(token)) {
                    throw new IllegalArgumentException("유효하지 않은 JWT 토큰입니다.");
                }

                String email = jwtTokenProvider.getEmailFromToken(token);
                String current = customTokenExpiredStrategy.get(email);
                if (current == null || !current.equals(token)) {
                    throw new IllegalArgumentException("다른 기기에서 로그인했거나 토큰이 무효화되었습니다.");
                }

                accessor.setUser(() -> email);
                accessor.setLeaveMutable(true);

                return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
            }

            return message;
        }
        // SUBSCRIBE
        if (StompCommand.SUBSCRIBE.equals(command)) {
            if (isPublic) return message;

            Object emailObj = accessor.getSessionAttributes().get("email");
            if (emailObj instanceof String email && !email.isBlank()) {
                userStatusService.touch(email);
            }
            return message;
        }

        return message;
    }

    // 인증이 필요한 destination 판별 메서드
    private boolean requiresAuth(String destination) {
        if (destination == null) return false;

        return destination.equals("/app/bid");
    }

    private String extractToken(StompHeaderAccessor accessor) {
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

        if (token.isBlank()) throw new IllegalArgumentException("JWT 토큰이 비어있습니다.");
        return token;
    }

    private String firstNonNull(String... xs) {
        for (String x : xs) if (x != null) return x;
        return null;
    }

//    private String normalizeRole(String raw) {
//        String s = String.valueOf(raw == null ? "" : raw).trim().toUpperCase();
//        if (s.startsWith("ROLE_")) s = s.substring(5);
//        return s;
//    }
}
