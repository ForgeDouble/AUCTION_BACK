package com.example.auction.chat.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ChatStateService {

    private final StringRedisTemplate redisTemplate;

    public ChatStateService(@Qualifier("chatState") StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    private static final Duration TTL_ROOM_PRESENCE = Duration.ofHours(6);
    private static final Duration TTL_COUNTER = Duration.ofDays(14);
    private String keyUserRoom(String email) { return "chat:user_room:" + email; }
    private String keyUserAlarm(String email) { return "chat:user_alarm:" + email; }
    private String keyUnread(String roomId, String email) { return "chat:unread:" + roomId + ":" + email; }

    // 사용자 입장 방
    public void enterRoom(String email, String roomId) {
        redisTemplate.opsForValue().set(keyUserRoom(email), roomId, TTL_ROOM_PRESENCE);
    }

    // 퇴장 시 기록 삭제
    public void exitRoom(String email) {
        redisTemplate.delete(keyUserRoom(email));
    }
    // 현 있는 방 조회

    public String currentRoomOf(String email) {
        return redisTemplate.opsForValue().get(keyUserRoom(email));
    }

    // 전체 알림 수
    public int getAlarm(String email) {
        String v = redisTemplate.opsForValue().get(keyUserAlarm(email));
        try { return v == null ? 0 : Integer.parseInt(v); }
        catch (Exception e) { return 0; }
    }

    public void setAlarm(String email, int n) {
        redisTemplate.opsForValue().set(keyUserAlarm(email), Integer.toString(Math.max(0, n)), TTL_COUNTER);
    }

    // 알림 개수 증가 메서드
    public void incAlarm(String email) {
        String k = keyUserAlarm(email);
        try {
            Long v = redisTemplate.opsForValue().increment(k);
            redisTemplate.expire(k, TTL_COUNTER);
            if (v == null) setAlarm(email, getAlarm(email) + 1);
        } catch (Exception e) {
            redisTemplate.opsForValue().set(k, "0", TTL_COUNTER);
            redisTemplate.opsForValue().increment(k);
            redisTemplate.expire(k, TTL_COUNTER);
        }
    }

    // 알림 초기화
    public void clearAlarm(String email) {
        redisTemplate.delete(keyUserAlarm(email));
    }

    public void incUnread(String roomId, String email) {
        String k = keyUnread(roomId, email);
        try {
            Long v = redisTemplate.opsForValue().increment(k);
            redisTemplate.expire(k, TTL_COUNTER);
            if (v == null) {
                String cur = redisTemplate.opsForValue().get(k);
                int n = 0;
                try { n = (cur == null) ? 0 : Integer.parseInt(cur); } catch (Exception ignored) {}
                redisTemplate.opsForValue().set(k, Integer.toString(n + 1), TTL_COUNTER);
            }
        } catch (Exception e) {
            redisTemplate.opsForValue().set(k, "0", TTL_COUNTER);
            redisTemplate.opsForValue().increment(k);
            redisTemplate.expire(k, TTL_COUNTER);
        }
    }

    // 해당 방 기준 미읽음 수 조회
    public int getUnread(String roomId, String email) {
        String v = redisTemplate.opsForValue().get(keyUnread(roomId, email));
        try { return v == null ? 0 : Integer.parseInt(v); }
        catch (Exception e) { return 0; }
    }


    // 해당 방 기준 미읽음 수 설정
    public void setUnread(String roomId, String email, int n) {
        redisTemplate.opsForValue().set(keyUnread(roomId, email), Integer.toString(Math.max(0, n)), TTL_COUNTER);
    }


    // 해당 방 기준 미읽음 초기화
    public void clearUnread(String roomId, String email) {
        redisTemplate.delete(keyUnread(roomId, email));
    }
}
