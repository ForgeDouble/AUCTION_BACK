package com.example.auction.user.service;

import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import com.example.auction.user.domain.UserStatus;
import com.example.auction.user.dto.DailyActiveUserStatsDto;
import com.example.auction.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class UserStatusService {

    private static final String ONLINE_KEY_PREFIX = "presence:online:";
    private static final String DAILY_KEY_PREFIX = "presence:daily:";

    @Qualifier("login")
    private final RedisTemplate<String, Object> loginRedisTemplate;

    @Qualifier("presence")
    private final StringRedisTemplate presenceStringRedisTemplate;
    private final UserRepository userRepository;

    public UserStatusService(@Qualifier("login")RedisTemplate<String, Object> loginRedisTemplate, @Qualifier("presence")StringRedisTemplate presenceStringRedisTemplate, UserRepository userRepository) {
        this.loginRedisTemplate = loginRedisTemplate;
        this.presenceStringRedisTemplate = presenceStringRedisTemplate;
        this.userRepository = userRepository;
    }

    private String onlineKey(String email) {
        return ONLINE_KEY_PREFIX + email;
    }


    //  presence:daily:2025-11-25 키 값으로 셋팅
    private String dailyKey(LocalDate date) {
        return DAILY_KEY_PREFIX + date.toString();
    }

    // 로그인 또는 활동 발생 시마다 호출 → 5분 동안 ONLINE 유지
    // 추가로 일일 사용자 / 사용자 카운팅을 위한 redis 셋팅
    public void touch(String email) {
        presenceStringRedisTemplate
                .opsForValue()
                .set(onlineKey(email), "1", Duration.ofMinutes(5));
        LocalDate today = LocalDate.now();
        String todayKey = dailyKey(today);

        presenceStringRedisTemplate
                .opsForSet()
                .add(todayKey, email);

        presenceStringRedisTemplate
                .expire(todayKey, Duration.ofDays(7));
    }

    // 로그아웃 시 상태 키 정리
    public void clear(String email) {
        presenceStringRedisTemplate.delete(onlineKey(email));
    }

    /* 현 상태 조회 */
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

    /* 특정 날짜 의 접속자 수 조회 */
    public Set<String> getDailyActiveEmails(LocalDate date) {
        String key = dailyKey(date);
        Set<String> members = presenceStringRedisTemplate.opsForSet().members(key);
        return (members != null) ? members : Set.of();
    }

    /* [통계용] authority가 USER 통계 */
    public DailyActiveUserStatsDto getDailyActiveUserStats(String dateText) {
        LocalDate date = (dateText == null || dateText.isBlank())
                ? LocalDate.now()
                : LocalDate.parse(dateText);

        Set<String> rawEmails = getDailyActiveEmails(date);

        List<String> filteredEmails = rawEmails.stream()
                .map(email -> userRepository.findByEmail(email).orElse(null))
                .filter(Objects::nonNull)
                .filter(user -> user.getAuthority() == Authority.USER)
                .map(User::getEmail)
                .collect(Collectors.toList());

        return DailyActiveUserStatsDto.dailyActiveUserStatsDto(date, filteredEmails);
    }

}
