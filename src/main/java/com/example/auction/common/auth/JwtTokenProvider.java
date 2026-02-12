package com.example.auction.common.auth;

import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secretKeyRT}")
    private String secretKey;

    @Value("${jwt.expirationRT}")
    private long expirationTime;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 64) {
            throw new IllegalStateException("jwt.secretKeyRT 가 너무 짧습니다. HS512는 64바이트 이상 권장입니다.");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }


    /* 토큰 생성 */
    public String createAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();

        claims.put("email", user.getEmail());
        claims.put("uid", user.getUserId());
        claims.put("nick", user.getNickname());
        claims.put("purl", user.getProfileImageUrl());
        claims.put("authority", user.getAuthority().name());

        long now = System.currentTimeMillis();

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getEmail())
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + expirationTime))
                .signWith(signingKey, SignatureAlgorithm.HS512)
                .compact();
    }

    // 만료 토큰도 claims 추출 가능 코드
    private Claims parseClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }

    /* 토큰에서 Claims 추출 */
    public Claims getClaimsFromToken(String token) {
        try {
            return parseClaims(token);
        } catch (ExpiredJwtException e) {
            throw new IllegalArgumentException("만료된 JWT 토큰입니다.", e);
        } catch (UnsupportedJwtException e) {
            throw new IllegalArgumentException("지원하지 않는 JWT 토큰입니다.", e);
        } catch (MalformedJwtException e) {
            throw new IllegalArgumentException("잘못된 형식의 JWT 토큰입니다.", e);
        } catch (SignatureException e) {
            throw new IllegalArgumentException("JWT 서명 검증에 실패했습니다.", e);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("JWT 토큰이 비어있습니다.", e);
        }
    }

    /* 토큰에서 이메일 추출 */
    public String getEmailFromToken(String token) {
        return getClaimsFromToken(token).getSubject();
    }

    /* 토큰에서 권한 추출 */
    public String getAuthorityFromToken(String token) {
        Object v = parseClaims(token).get("authority");
        return v == null ? null : String.valueOf(v);
    }

    //  채팅 핫패스용
    public Long getUserIdFromToken(String token) {
        Object v = parseClaims(token).get("uid");
        if (v == null) return null;
        return Long.parseLong(String.valueOf(v));
    }

    public String getNicknameFromToken(String token) {
        Object v = parseClaims(token).get("nick");
        return v == null ? null : String.valueOf(v);
    }

    public String getProfileUrlFromToken(String token) {
        Object v = parseClaims(token).get("purl");
        return v == null ? null : String.valueOf(v);
    }

    public Authority getAuthorityEnumFromToken(String token) {
        Object v = parseClaims(token).get("authority");
        if (v == null) return null;
        return Authority.valueOf(String.valueOf(v));
    }

    

    /* 토큰 유효성 검사 */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(token);
            return true;
        } catch (io.jsonwebtoken.ExpiredJwtException
                 | io.jsonwebtoken.UnsupportedJwtException
                 | io.jsonwebtoken.MalformedJwtException
                 | io.jsonwebtoken.SignatureException
                 | IllegalArgumentException e) {
             log.debug("JWT invalid: {}", e.toString());
            return false;
        }
    }

    /* 토큰 만료 여부 확인 */
    private boolean isTokenExpired(String token) {
        return getClaimsFromToken(token).getExpiration().before(new Date());
    }

    /* 토큰 만료 시간 추가 */
    public long getRemainingSeconds(String token) {
        try {
            Claims claims = parseClaims(token);
            Date exp = claims.getExpiration();
            if (exp == null) return 0;

            long expMillis = exp.getTime();
            long nowMillis = System.currentTimeMillis();
            return Math.max(0, (expMillis - nowMillis) / 1000);
        } catch (Exception e) {
            log.warn("JWT expired: {}", e.toString());
            return 0;
        }
    }
}
