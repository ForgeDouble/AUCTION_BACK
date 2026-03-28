package com.example.auction.common.config;


import com.example.auction.chat.handler.StompHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final StompHandler stompHandler;
    private static final String[] ALLOWED_ORIGINS = {
            "https://auctionbid.shop",
            "https://www.auctionbid.shop"
    };

    public WebSocketConfig(StompHandler stompHandler) {
        this.stompHandler = stompHandler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
//        클라이언트 구독
        registry.enableSimpleBroker("/topic", "/queue");
//        클라이언트 발신
        registry.setApplicationDestinationPrefixes("/app");
//        특정 유저 발신
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 인증 필요한 비공개 엔드포인트 (TEST 시 withSockJS 주석 처리 필요)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new HttpSessionHandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(
                            ServerHttpRequest request,
                            ServerHttpResponse response,
                            WebSocketHandler wsHandler,
                            Map<String, Object> attributes) throws Exception {
                        attributes.put("endpointType", "private");
                        return super.beforeHandshake(request, response, wsHandler, attributes);
                    }
                })
                .withSockJS();

        // 인증 불필요한 공개 경매용 엔드포인트 (TEST 시 withSockJS 주석 처리 필요)
        registry.addEndpoint("/ws-public")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new HttpSessionHandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(
                            ServerHttpRequest request,
                            ServerHttpResponse response,
                            WebSocketHandler wsHandler,
                            Map<String, Object> attributes) throws Exception {
                        attributes.put("endpointType", "public");
                        return super.beforeHandshake(request, response, wsHandler, attributes);
                    }
                })
                .withSockJS();

        // 관리자 전용 (TEST 시 withSockJS 주석 처리 필요)
        registry.addEndpoint("/ws-admin")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new HttpSessionHandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(
                            ServerHttpRequest request,
                            ServerHttpResponse response,
                            WebSocketHandler wsHandler,
                            Map<String, Object> attributes) throws Exception {
                        attributes.put("endpointType", "admin");
                        return super.beforeHandshake(request, response, wsHandler, attributes);
                    }
                })
                .withSockJS();
    }



    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompHandler);
    }
}
