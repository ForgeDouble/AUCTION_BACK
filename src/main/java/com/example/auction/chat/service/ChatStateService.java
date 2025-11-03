package com.example.auction.chat.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class ChatStateService {

    private final RedisTemplate<String, Object> redisTemplate;

    public ChatStateService(@Qualifier("chatRoom") RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String keyUserRoom(String userId) { return "user_" + userId; }
    private String keyUserAlarm(String userId) { return "user_alarm_" + userId; }
    private String keyUnread(String roomId, String userId) { return "chatRoom_" + roomId + "_" + userId; }

    // 사용자 입장 방
    public void enterRoom(String userId, String roomId) {
        redisTemplate.opsForValue().set(keyUserRoom(userId), roomId);
    }

    // 퇴장 시 기록 삭제
    public void exitRoom(String userId) {
        redisTemplate.delete(keyUserRoom(userId));
    }

    // 현 있는 방 조회
    public String currentRoomOf(String userId) {
        Object v = redisTemplate.opsForValue().get(keyUserRoom(userId));
        return v == null ? null : v.toString();
    }

    // 전체 알림 수
    public int getAlarm(String userId) {
        Object v = redisTemplate.opsForValue().get(keyUserAlarm(userId));
        try { return v == null ? 0 : Integer.parseInt(v.toString()); }
        catch (Exception e) { return 0; }
    }

    public void setAlarm(String userId, int n) {
        redisTemplate.opsForValue().set(keyUserAlarm(userId), Integer.toString(Math.max(0, n)));
    }

    // 알림 개수 증가 메서드
    public void incAlarm(String userId) {
        setAlarm(userId, getAlarm(userId) + 1);
    }

    // 알림 초기화
    public void clearAlarm(String userId) {
        redisTemplate.delete(keyUserAlarm(userId));
    }

    public void incUnread(String roomId, String userId) {
        String k = keyUnread(roomId, userId);
        Object v = redisTemplate.opsForValue().get(k);
        int n = 0;
        try { n = (v == null) ? 0 : Integer.parseInt(v.toString()); }
        catch (Exception ignored) {}
        redisTemplate.opsForValue().set(k, Integer.toString(n + 1));
    }

    // 해당 방 기준 미읽음 수 조회
    public int getUnread(String roomId, String userId) {
        Object v = redisTemplate.opsForValue().get(keyUnread(roomId, userId));
        try { return v == null ? 0 : Integer.parseInt(v.toString()); }
        catch (Exception e) { return 0; }
    }


    // 해당 방 기준 미읽음 수 설정
    public void setUnread(String roomId, String userId, int n) {
        redisTemplate.opsForValue().set(keyUnread(roomId, userId), Integer.toString(Math.max(0, n)));
    }


    // 해당 방 기준 미읽음 초기화
    public void clearUnread(String roomId, String userId) {
        redisTemplate.delete(keyUnread(roomId, userId));
    }
}
