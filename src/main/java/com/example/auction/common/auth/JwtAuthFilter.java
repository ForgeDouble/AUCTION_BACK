package com.example.auction.common.auth;

import com.example.auction.common.service.CustomTokenExpiredStrategy;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.service.CustomUserService;
import com.example.auction.user.service.UserService;
import com.example.auction.user.service.UserStatusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
//    private final CustomUserService customUserService;
    private final CustomTokenExpiredStrategy customTokenExpiredStrategy;
    private final UserStatusService userStatusService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String authorizationHeader = request.getHeader("Authorization");

        try {
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                String token = authorizationHeader.substring(7).trim();

                // 토큰 검증(서명/만료)
                if (!jwtTokenProvider.validateToken(token)) {
                    unauthorized(response, "INVALID_TOKEN", "만료되었거나 유효하지 않은 JWT 토큰입니다.");
                    return;
                }

                // claims 기반 추출
                String email = jwtTokenProvider.getEmailFromToken(token);
                if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                    // [STEP2] 단일 세션(로그인 Redis) 체크는 유지
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

                    List<SimpleGrantedAuthority> roles = (authority == null) ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + authority.name()));

                    AuthUserPrincipal principal =
                            new AuthUserPrincipal(uid, email, authority, nick, purl);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    // presence 관련
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
        response.setContentType("text/plain; charset=UTF-8");

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("statusCode", errorCode);
        errorResponse.put("errorMessage", msg);
        errorResponse.put("additionalInfo", null);

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
