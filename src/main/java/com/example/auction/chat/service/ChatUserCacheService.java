package com.example.auction.chat.service;

import com.example.auction.chat.dto.ChatUserSummary;
import com.example.auction.common.domain.DelYN;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class ChatUserCacheService {

    private static final String KEY_PREFIX = "chat:user:";

    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public ChatUserCacheService(
            UserRepository userRepository,
            @Qualifier("chatRoom") RedisTemplate<String, Object> redisTemplate
    ) {
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
    }

    private String key(String email) {
        return KEY_PREFIX + email;
    }

    // email 기준으로 캐시 조회 → 없으면 DB 1번 조회 후 Redis에 넣고 재사용
    public ChatUserSummary getByEmail(String email) {
        Object cached = redisTemplate.opsForValue().get(key(email));
        if (cached instanceof ChatUserSummary summary) {
            return summary;
        }

        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new RuntimeException("유저를 찾을 수 없습니다. email=" + email));

        ChatUserSummary summary = new ChatUserSummary(
                user.getUserId(),
                user.getEmail(),
                user.getNickname(),
                user.getAuthority(),
                user.getProfileImageUrl()
        );

        // 1~2시간 정도 캐시 (원하는 TTL로 조정 가능)
        redisTemplate.opsForValue().set(key(email), summary, Duration.ofHours(2));

        return summary;
    }

    // 현재 로그인 유저용 헬퍼
    public ChatUserSummary getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        if (email == null || email.isBlank()) {
            throw new RuntimeException("인증 정보가 없습니다.");
        }
        return getByEmail(email);
    }

    // 닉네임 변경 등으로 캐시 갱신하고 싶을 때 사용할 수 있는 메서드
    public void evict(String email) {
        redisTemplate.delete(key(email));
    }


}
