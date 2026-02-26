package com.example.auction.chat.service;

import com.example.auction.chat.dto.ChatUserSummary;
import com.example.auction.common.auth.SecurityUserContext;
import com.example.auction.common.domain.DelYN;
import com.example.auction.user.repository.UserRepository;
import com.example.auction.user.repository.UserSummaryProjection;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Service
public class ChatUserCacheService {

    private static final String KEY_PREFIX = "chat:user:";

    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    public ChatUserCacheService(
            UserRepository userRepository,
            @Qualifier("chatRoom") RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    private String key(String email) {
        return KEY_PREFIX + email;
    }

    public ChatUserSummary getCurrentUser() {
        return SecurityUserContext.chatSummary();
    }

    public ChatUserSummary getByEmail(String email) {
        Map<String, ChatUserSummary> map = getByEmails(List.of(email));
        ChatUserSummary summary = map.get(email);
        if (summary == null)
            throw new RuntimeException("유저를 찾을 수 없습니다. email=" + email);
        return summary;
    }
    private ChatUserSummary toSummary(Object cached) {
        if (cached == null) return null;

        if (cached instanceof ChatUserSummary s) return s;

        if (cached instanceof java.util.Map<?, ?> map) {
            try {
                return objectMapper.convertValue(map, ChatUserSummary.class);
            } catch (Exception ignored) {
                return null;
            }
        }
        if (cached instanceof String str) {
            try {
                return objectMapper.readValue(str, ChatUserSummary.class);
            } catch (Exception ignored) {
                return null;
            }
        }

        return null;
    }
    // 현재 로그인 유저용 헬퍼
    public Map<String, ChatUserSummary> getByEmails(Collection<String> emails) {
        if (emails == null || emails.isEmpty()) return Map.of();

        List<String> emailList = emails.stream()
                .filter(email -> email != null && !email.isBlank())
                .distinct()
                .toList();

        List<String> keys = emailList.stream().map(this::key).toList();

        List<Object> cachedList = redisTemplate.opsForValue().multiGet(keys);

        Map<String, ChatUserSummary> result = new HashMap<>();
        List<String> missing = new ArrayList<>();

        for (int i = 0; i < emailList.size(); i++) {
            Object c = (cachedList == null) ? null : cachedList.get(i);
            String email = emailList.get(i);

            ChatUserSummary summary = toSummary(c);
            if (summary != null) {
                result.put(email, summary);
            } else {
                missing.add(email);
            }
        }

        if (!missing.isEmpty()) {
            List<UserSummaryProjection> rows = userRepository.findUserSummariesByEmails(missing, DelYN.N);

            for (UserSummaryProjection row : rows) {
                ChatUserSummary summary = ChatUserSummary.builder()
                        .userId(row.getUserId())
                        .email(row.getEmail())
                        .nickname(row.getNickname())
                        .authority(row.getAuthority())
                        .profileImageUrl(row.getProfileImageUrl())
                        .build();

                result.put(row.getEmail(), summary);

                redisTemplate.opsForValue().set(
                        key(row.getEmail()),
                        summary,
                        Duration.ofHours(2)
                );
            }
        }

        return result;
    }

    // 닉네임 변경 등으로 캐시 갱신하고 싶을 때 사용할 수 있는 메서드
    public void evict(String email) {
        redisTemplate.delete(key(email));
    }


}
