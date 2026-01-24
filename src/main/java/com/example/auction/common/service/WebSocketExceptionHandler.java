package com.example.auction.common.service;

import com.example.auction.bid.dto.ErrorMessageDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

@Slf4j
@ControllerAdvice
public class WebSocketExceptionHandler {

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public ErrorMessageDto handleException(Exception e) {
        log.error("[WebSocket] 전역 예외 발생", e);

        // 클라이언트에게 보낼 에러 메시지 생성
        String userMessage = "요청 처리 중 오류가 발생했습니다.";
        String errorCode = "WEBSOCKET_ERROR";

        // 특정 예외 타입별 메시지 커스터마이징
        if (e instanceof IllegalArgumentException) {
            userMessage = e.getMessage();
            errorCode = "INVALID_REQUEST";
        } else if (e instanceof RuntimeException) {
            userMessage = e.getMessage();
            errorCode = "RUNTIME_ERROR";
        }

        return new ErrorMessageDto(userMessage, errorCode, System.currentTimeMillis());
    }
}

