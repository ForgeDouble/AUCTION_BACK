package com.example.auction.common.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
public class FcmConfig {

    @Value("${firebase.service-account-b64:}")  // Base64 인코딩된 JSON을 yml/env로 주입
    private String serviceAccountB64;

    @Value("${firebase.service-account-json:}") // 생 JSON 문자열을 yml/env로 주입
    private String serviceAccountJson;

    @Value("${firebase.service-account-path:}") // (옵션) 기존 파일/클래스패스 경로
    private Resource serviceAccountPath;

    @Bean
    public FirebaseApp firebaseApp() throws Exception {
        // 1) Base64 환경변수 우선
        if (serviceAccountB64 != null && !serviceAccountB64.isBlank()) {
            byte[] decoded = Base64.getDecoder().decode(serviceAccountB64.trim());
            return initFromStream(new ByteArrayInputStream(decoded));
        }

        // 2) 생 JSON 문자열
        if (serviceAccountJson != null && !serviceAccountJson.isBlank()) {
            byte[] bytes = serviceAccountJson.getBytes(StandardCharsets.UTF_8);
            return initFromStream(new ByteArrayInputStream(bytes));
        }

        // 3) GCP/ADC(Workload Identity or GOOGLE_APPLICATION_CREDENTIALS)
        //    - GCP 런너/클러스터면 파일 없이 이걸로 동작 (권한만 맞으면 됨)
        if (isDefaultCredentialsAvailable()) {
            var options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .build();
            return initOnce(options);
        }

        // 4) (fallback) 파일/클래스패스 경로
        if (serviceAccountPath != null && serviceAccountPath.exists()) {
            try (InputStream in = serviceAccountPath.getInputStream()) {
                return initFromStream(in);
            }
        }

        throw new IllegalStateException("""
            Firebase 자격증명을 찾을 수 없습니다.
            - firebase.service-account-b64 (Base64 JSON)
            - firebase.service-account-json (생 JSON)
            - GOOGLE_APPLICATION_CREDENTIALS / ADC
            - firebase.service-account-path
            중 하나를 설정하세요.
        """);
    }

    private boolean isDefaultCredentialsAvailable() {
        try {
            GoogleCredentials.getApplicationDefault(); // 가용성 체크
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private FirebaseApp initFromStream(InputStream in) throws Exception {
        var options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(in))
                .build();
        return initOnce(options);
    }

    private FirebaseApp initOnce(FirebaseOptions options) {
        if (FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.initializeApp(options);
        }
        return FirebaseApp.getInstance();
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp app) {
        return FirebaseMessaging.getInstance(app);
    }
}
