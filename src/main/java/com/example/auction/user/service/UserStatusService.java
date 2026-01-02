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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class UserStatusService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final String ONLINE_KEY_PREFIX = "presence:online:";
    private static final String DAILY_KEY_PREFIX  = "presence:daily:";

    // 실시간 카운트(ZSET)
    private static final String ONLINE_ZSET_KEY = "presence:online:z";
    // onlineKey TTL이 5분 -> 윈도우도 5분
    private static final long ONLINE_WINDOW_SEC = 300;

    // 시간대별 유니크 유저
    // key 세팅값 : presence:hourly:2025-12-26:13 (members = email)
    private static final String HOURLY_KEY_PREFIX = "presence:hourly:";

    @Qualifier("login")
    private final RedisTemplate<String, Object> loginRedisTemplate;

    @Qualifier("presence")
    private final StringRedisTemplate presenceStringRedisTemplate;

    private final UserRepository userRepository;

    public UserStatusService(
            @Qualifier("login") RedisTemplate<String, Object> loginRedisTemplate,
            @Qualifier("presence") StringRedisTemplate presenceStringRedisTemplate,
            UserRepository userRepository
    ) {
        this.loginRedisTemplate = loginRedisTemplate;
        this.presenceStringRedisTemplate = presenceStringRedisTemplate;
        this.userRepository = userRepository;
    }

    private String onlineKey(String email) {
        return ONLINE_KEY_PREFIX + email;
    }

    // presence:daily:2025-11-25
    private String dailyKey(LocalDate date) {
        return DAILY_KEY_PREFIX + date;
    }

    // presence:hourly:2025-12-26:13
    private String hourlyKey(LocalDate date, int hour) {
        return HOURLY_KEY_PREFIX + date + ":" + String.format("%02d", hour);
    }

    // 로그인 / 활동 시 호출
    public void touch(String email) {
        if (email == null || email.isBlank()) return;

        // 5분 온라인 유지
        presenceStringRedisTemplate
                .opsForValue()
                .set(onlineKey(email), "1", Duration.ofMinutes(5));

        LocalDate today = LocalDate.now(KST);
        String todayKey = dailyKey(today);

        presenceStringRedisTemplate.opsForSet().add(todayKey, email);
        presenceStringRedisTemplate.expire(todayKey, Duration.ofDays(7));

        // 실시간 카운트용 ZSET (멤버=email, score=현재초)
        long nowSec = System.currentTimeMillis() / 1000;
        presenceStringRedisTemplate.opsForZSet().add(ONLINE_ZSET_KEY, email, nowSec);

        // 금일 사용 시간대(유니크 유저) 기록
        int hour = LocalDateTime.now(KST).getHour();
        String hk = hourlyKey(today, hour);
        presenceStringRedisTemplate.opsForSet().add(hk, email);
        presenceStringRedisTemplate.expire(hk, Duration.ofDays(7));
    }

    public void clear(String email) {
        presenceStringRedisTemplate.delete(onlineKey(email));
        // presenceStringRedisTemplate.opsForZSet().remove(ONLINE_ZSET_KEY, email);
    }

    // 상태 조회
    public UserStatus getStatus(String email) {
        Boolean loggedIn = loginRedisTemplate.hasKey(email);
        if (!Boolean.TRUE.equals(loggedIn)) return UserStatus.OFFLINE;

        Boolean active = presenceStringRedisTemplate.hasKey(onlineKey(email));
        return Boolean.TRUE.equals(active) ? UserStatus.ONLINE : UserStatus.IDLE;
    }

    public Set<String> getDailyActiveEmails(LocalDate date) {
        String key = dailyKey(date);
        Set<String> members = presenceStringRedisTemplate.opsForSet().members(key);
        return (members != null) ? members : Set.of();
    }

    //금일 접속 유저 카운트 - 관리자 대시보드
    public long getDailyActiveCount(LocalDate date) {
        Long count = presenceStringRedisTemplate.opsForSet().size(dailyKey(date));
        return (count == null) ? 0 : count;
    }

    // 실시간 접속자 카운트 - 관리자 대시 보드
    public long getRealtimeUsersCount() {
        long nowSec = System.currentTimeMillis() / 1000;
        long min = nowSec - ONLINE_WINDOW_SEC;

        // 오래된 항목 정리
        presenceStringRedisTemplate.opsForZSet().removeRangeByScore(ONLINE_ZSET_KEY, 0, min - 1);

        Long c = presenceStringRedisTemplate.opsForZSet().zCard(ONLINE_ZSET_KEY);
        return (c == null) ? 0 : c;
    }

    public List<HourlyPoint> getHourlySeries(LocalDate date) {
        List<HourlyPoint> hourlyPoints = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            String key = hourlyKey(date, h);
            Long c = presenceStringRedisTemplate.opsForSet().size(key);
            hourlyPoints.add(new HourlyPoint(h, (c == null) ? 0 : c));
        }
        return hourlyPoints;
    }

    public record HourlyPoint(int hour, long users) {}

    // 권한 -> USER 필터링 이메일 리스트 제공
    public DailyActiveUserStatsDto getDailyActiveUserStats(String dateText) {
        LocalDate date = (dateText == null || dateText.isBlank())
                ? LocalDate.now(KST)
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