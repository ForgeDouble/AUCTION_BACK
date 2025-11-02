package com.example.auction.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatStateService {

    @Qualifier("chatRoom")
    private final RedisTemplate<String, Object> redisTemplate;

    private String keyUserRoom(String userId){ return "user_"+userId; }
    private String keyUserAlarm(String userId){ return "user_alarm_"+userId; }
    private String keyUnread(String roomId, String userId){ return "chatRoom_"+roomId+"_"+userId; }

    public void enterRoom(String userId, String roomId){
        redisTemplate.opsForValue().set(keyUserRoom(userId), roomId);
    }

    public void exitRoom(String userId){
        redisTemplate.delete(keyUserRoom(userId));
    }

    public String currentRoomOf(String userId){
        Object v = redisTemplate.opsForValue().get(keyUserRoom(userId));
        return v==null? null : v.toString();
    }

    public int getAlarm(String userId){
        Object v = redisTemplate.opsForValue().get(keyUserAlarm(userId));
        return v==null?0:Integer.parseInt(v.toString());
    }

    public void setAlarm(String userId, int n){
        redisTemplate.opsForValue().set(keyUserAlarm(userId), Integer.toString(n));
    }

    public void incUnread(String roomId, String userId){
        String k = keyUnread(roomId,userId);
        Object v = redisTemplate.opsForValue().get(k);
        int n = v==null?0:Integer.parseInt(v.toString());
        redisTemplate.opsForValue().set(k, Integer.toString(n+1));
    }

    public int getUnread(String roomId, String userId){
        Object v = redisTemplate.opsForValue().get(keyUnread(roomId,userId));
        return v==null?0:Integer.parseInt(v.toString());
    }

    public void clearUnread(String roomId, String userId){
        redisTemplate.delete(keyUnread(roomId,userId));
    }
}
