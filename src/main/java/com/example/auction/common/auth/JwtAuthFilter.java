package com.example.auction.common.auth;

import com.example.auction.common.service.CustomTokenExpiredStrategy;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.service.UserStatusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomTokenExpiredStrategy customTokenExpiredStrategy;
    private final UserStatusService userStatusService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("OPTIONS".equalsIgnoreCase(method)
                || "/user/login".equals(path)
                || "/user/register".equals(path)
//                || "/user/verify-token".equals(path)
                || "/actuator/health".equals(path)
                || path.startsWith("/product")
                || path.startsWith("/category")
                || path.startsWith("/bid")
                || path.startsWith("/auth")
                || path.startsWith("/season")
                || path.startsWith("/ws")) {
            chain.doFilter(request, response);
            return;
        }

        String authorizationHeader = request.getHeader("Authorization");

        try {
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                String token = authorizationHeader.substring(7).trim();

                if (!jwtTokenProvider.validateToken(token)) {
                    unauthorized(response, "INVALID_TOKEN", "만료되었거나 유효하지 않은 JWT 토큰입니다.");
                    return;
                }

                String email = jwtTokenProvider.getEmailFromToken(token);
                if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    String current = customTokenExpiredStrategy.get(email);
                    if (current == null || !current.equals(token)) {
                        unauthorized(response, "INVALID_TOKEN", "다른 기기에서 로그인했거나 토큰이 무효화되었습니다.");
                        return;
                    }

                    Long uid = jwtTokenProvider.getUserIdFromToken(token);
                    String nick = jwtTokenProvider.getNicknameFromToken(token);
                    String purl = jwtTokenProvider.getProfileUrlFromToken(token);

                    Authority authority = jwtTokenProvider.getAuthorityEnumFromToken(token);
                    if (authority == null) {
                        String a = jwtTokenProvider.getAuthorityFromToken(token);
                        if (a != null && !a.isBlank()) {
                            authority = Authority.valueOf(a);
                        }
                    }

                    AuthUserPrincipal principal = new AuthUserPrincipal(uid, email, authority, nick, purl);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    userStatusService.touch(email);
                }
            }

            chain.doFilter(request, response);

        } catch (Exception e) {
            unauthorized(response, "INVALID_TOKEN", "유효하지 않거나 만료된 JWT 토큰입니다.");
        }
    }

    private void unauthorized(HttpServletResponse response, String errorCode, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("statusCode", errorCode);
        errorResponse.put("errorMessage", msg);
        errorResponse.put("additionalInfo", null);

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

}