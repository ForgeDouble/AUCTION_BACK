package com.example.auction.common.service;

import com.example.auction.chat.dto.ChatMessageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    @Qualifier("chatObjectMapper")
    private final ObjectMapper chatObjectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            ChatMessageResponse dto = chatObjectMapper.readValue(json, ChatMessageResponse.class);
            if (dto.getRoomId() != null) {
                messagingTemplate.convertAndSend("/topic/chat/room/" + dto.getRoomId(), dto);
            }
        } catch (Exception e) {
            log.error("pubsub deserialization failed", e);
        }
    }
}
