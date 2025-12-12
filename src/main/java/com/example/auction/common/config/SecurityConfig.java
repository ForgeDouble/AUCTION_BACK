package com.example.auction.common.config;

import com.example.auction.common.auth.JwtAuthFilter;
import com.example.auction.common.auth.JwtTokenProvider;
import com.example.auction.user.service.CustomUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserService customUserService;



    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }



    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // 세션 비활성화
//                        .sessionFixation().changeSessionId() // 고정 공격 방지
//                        .maximumSessions(1) // 동시 세션 제한 = 1
//                        .maxSessionsPreventsLogin(false) // 중복 로그인 제어
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/user/login",
                                "/user/register",
                                "/user/verify-token",
                                "/product/{productId}",
                                "/product/all",
                                "/bid/**",
                                "/category/**",
                                "/ws/**",
                                "/ws-public/**"

                        ).permitAll()  // 로그인, 회원가입 경로는 인증x
                        .anyRequest().authenticated() // 나머지 토큰값 필요
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
