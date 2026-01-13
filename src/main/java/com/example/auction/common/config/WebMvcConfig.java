package com.example.auction.common.config;

import com.example.auction.admin.metrics.interceptor.UserActivityMetricsInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final UserActivityMetricsInterceptor userActivityMetricsInterceptor;

    public WebMvcConfig(UserActivityMetricsInterceptor userActivityMetricsInterceptor) {
        this.userActivityMetricsInterceptor = userActivityMetricsInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userActivityMetricsInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/error",
                        "/favicon.ico",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/actuator/**"
                );
    }
}
