package com.example.auction.common.auth;

import com.example.auction.user.domain.User;
import io.jsonwebtoken.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secretKeyRT}")
    private String secretKey;

    @Value("${jwt.expirationRT}")
    private long expirationTime;

    /* 토큰 생성 */
    public String createAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("authority", user.getAuthority());
        claims.put("email", user.getEmail());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getEmail())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(SignatureAlgorithm.HS512, secretKey)
                .compact();
    }

    /* 토큰에서 Claims 추출 */
    public Claims getClaimsFromToken(String token) {
        try {
            return Jwts.parser()
                    .setSigningKey(secretKey)
                    .parseClaimsJws(token)
                    .getBody();
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

    public String getAuthorityFromToken(String token) {
        return getClaimsFromToken(token).get("authority", String.class);
    }

    /* 토큰 유효성 검사 */
    public boolean validateToken(String token) {
        try {
            Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token);
            return !isTokenExpired(token);
        } catch (ExpiredJwtException | UnsupportedJwtException | MalformedJwtException | IllegalArgumentException e) {
            System.out.println("만료된 JWT 토큰입니다." + e.getMessage());
            return false;
        }
    }

    /* 토큰 만료 여부 확인 */
    private boolean isTokenExpired(String token) {
        return getClaimsFromToken(token).getExpiration().before(new Date());
    }
}
