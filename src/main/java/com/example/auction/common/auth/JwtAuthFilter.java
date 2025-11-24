package com.example.auction.common.auth;

import com.example.auction.common.service.CustomTokenExpiredStrategy;
import com.example.auction.user.service.CustomUserService;
import com.example.auction.user.service.UserService;
import com.example.auction.user.service.UserStatusService;
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
    private final UserStatusService userStatusService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String authorizationHeader = request.getHeader("Authorization");

        String token = null;

        try {
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                token = authorizationHeader.substring(7); // "Bearer " 제거


                if (!jwtTokenProvider.validateToken(token)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("만료되었거나 유효하지 않은 JWT 토큰입니다.");
                    return;
                }

                String email = jwtTokenProvider.getEmailFromToken(token);

                if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = customUserService.loadUserByUsername(email);

                    String current = customTokenExpiredStrategy.get(email);
                    if (current == null || !current.equals(token)) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.getWriter().write("다른 기기에서 로그인했거나 토큰이 무효화되었습니다.");
                        return;
                    }

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    userStatusService.touch(email);
                }
            }
            chain.doFilter(request, response);

        } catch (io.jsonwebtoken.ExpiredJwtException
                 | io.jsonwebtoken.UnsupportedJwtException
                 | io.jsonwebtoken.MalformedJwtException
                 | io.jsonwebtoken.SignatureException
                 | IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("유효하지 않거나 만료된 JWT 토큰입니다.");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("서버 내부 오류가 발생했습니다.");
        }
    }
}
