package com.example.auction.chat.handler;

import com.example.auction.common.auth.JwtTokenProvider;
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
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class StompHandler implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    // 이 코드가 웹소켓 실행 되기 전에 header 으로 전달해야하는 코드
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor stompHeaderAccessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT == stompHeaderAccessor.getCommand()) { // websocket 연결 요청

            // chatState
            String raw = firstNonNull(
                    stompHeaderAccessor.getFirstNativeHeader("Authorization"),
                    stompHeaderAccessor.getFirstNativeHeader("authorization"),
                    stompHeaderAccessor.getFirstNativeHeader("token")
            );
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("인증 헤더가 없습니다.");
            }
            String token = raw.startsWith("Bearer ") ? raw.substring(7) : raw.trim();
            jwtTokenProvider.validateToken(token);
        }
        return message;
    }

    private String firstNonNull(String... xs){
        for (String x : xs) if (x != null) return x;
        return null;
    }
}
