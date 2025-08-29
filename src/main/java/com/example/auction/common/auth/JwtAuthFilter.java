package com.example.auction.common.auth;

import com.example.auction.common.service.CustomTokenExpiredStrategy;
import com.example.auction.user.service.CustomUserService;
import com.example.auction.user.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserService customUserService;
    private final CustomTokenExpiredStrategy customTokenExpiredStrategy;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String authorizationHeader = request.getHeader("Authorization");

        String username = null;
        String token = null;

        try {
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                token = authorizationHeader.substring(7); // "Bearer " 제거 후 토큰 추출
                username = jwtTokenProvider.getEmailFromToken(token); // 토큰에서 파싱
            }

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = customUserService.loadUserByUsername(username);

                String current = customTokenExpiredStrategy.get(username);
                if (current == null || !current.equals(token)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("다른 기기에서 로그인했거나 토큰이 무효화되었습니다.");
                    return;
                }
                if (jwtTokenProvider.validateToken(token)) {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }  else {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("만료된 JWT 토큰입니다.");
                    return;
                }
            }
            chain.doFilter(request, response);

        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Access Token이 만료되었습니다.");
        } catch (io.jsonwebtoken.UnsupportedJwtException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("지원하지 않는 JWT 토큰입니다.");
        } catch (io.jsonwebtoken.MalformedJwtException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("잘못된 형식의 JWT 토큰입니다.");
        } catch (io.jsonwebtoken.SignatureException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("JWT 서명 검증에 실패했습니다.");
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("유효하지 않은 JWT 토큰입니다.");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("서버 내부 오류가 발생했습니다.");
        }
    }
}
