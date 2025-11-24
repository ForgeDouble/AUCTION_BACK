package com.example.auction.user.service;

import com.example.auction.user.domain.UserStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class UserStatusService {

    private static final String ONLINE_KEY_PREFIX = "presence:online:";

    @Qualifier("login")
    private final RedisTemplate<String, Object> loginRedisTemplate;

    @Qualifier("presence")
    private final StringRedisTemplate presenceStringRedisTemplate;

    public UserStatusService(@Qualifier("login")RedisTemplate<String, Object> loginRedisTemplate, @Qualifier("presence")StringRedisTemplate presenceStringRedisTemplate) {
        this.loginRedisTemplate = loginRedisTemplate;
        this.presenceStringRedisTemplate = presenceStringRedisTemplate;
    }

    private String onlineKey(String email) {
        return ONLINE_KEY_PREFIX + email;
    }

    // 로그인 또는 활동 발생 시마다 호출 → 5분 동안 ONLINE 유지
    public void touch(String email) {
        presenceStringRedisTemplate
                .opsForValue()
                .set(onlineKey(email), "1", Duration.ofMinutes(5));
    }

    // 로그아웃 시 상태 키 정리
    public void clear(String email) {
        presenceStringRedisTemplate.delete(onlineKey(email));
    }

    // 현재 상태 조회
    public UserStatus getStatus(String email) {
        Boolean loggedIn = loginRedisTemplate.hasKey(email);
        if (!Boolean.TRUE.equals(loggedIn)) {
            return UserStatus.OFFLINE;
        }

        // 최근 5분 활동 여부 체크
        Boolean active = presenceStringRedisTemplate.hasKey(onlineKey(email));
        if (Boolean.TRUE.equals(active)) {
            return UserStatus.ONLINE;  // 접속중 (로그인 )
        } else {
            return UserStatus.IDLE;    // 자리비움 (로그인 되어 있지만 5분간 활동 없음)
        }
    }


}
