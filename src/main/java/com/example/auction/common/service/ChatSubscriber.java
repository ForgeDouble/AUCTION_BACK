package com.example.auction.common.service;

import com.example.auction.chat.dto.ChatMessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatSubscriber implements MessageListener {
    private final SimpMessageSendingOperations messaging;
    private final GenericJackson2JsonRedisSerializer genericJackson2JsonRedisSerializer = new GenericJackson2JsonRedisSerializer();

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            Object obj = genericJackson2JsonRedisSerializer.deserialize(message.getBody());
            if (obj instanceof ChatMessageResponse chatMessageResponse) {
                    messaging.convertAndSend("/topic/chat/room/" + chatMessageResponse.getRoomId(), chatMessageResponse);
                } else {
                    log.warn("Unknown pubsub payload: {}", obj);
                }
        } catch (Exception e){
           log.error("pubsub deserialization failed", e);
        }
    }
}
