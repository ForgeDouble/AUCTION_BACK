package com.example.auction.common.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;


/*
* 배포 환경에서 credential 증명 방식이 다름
* 따라서 fcmConfig 에서 해당 자격을 받을 때 각각의 경우(if) 문을 활용해서 우선순위 적용 후 처리
* env(B64) -> JSON -> ADC -> FILE 순
* */
@Configuration
@Slf4j
public class FcmConfig {

    @Value("${firebase.service-account-b64:}")
    private String serviceAccountB64;

    @Value("${firebase.service-account-json:}")
    private String serviceAccountJson;

    @Value("${firebase.service-account-path:}")
    private Resource serviceAccountPath;

    @Bean
    public FirebaseApp firebaseApp() throws Exception {
        // Base64 환경변수 우선 적용
        if (serviceAccountB64 != null && !serviceAccountB64.isBlank()) {
            log.info("[FCM] B64 env 들어옴");
            byte[] decoded = Base64.getDecoder().decode(serviceAccountB64.trim());
            return initFromStream(new ByteArrayInputStream(decoded));
        }

        // 생 JSON 문자열
        if (serviceAccountJson != null && !serviceAccountJson.isBlank()) {
            log.info("[FCM] JSON 들어옴");
            return initFromStream(new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8)));
        }

        //ADC — 파일 없이 워크로드 아이덴티티
        try {
            GoogleCredentials.getApplicationDefault();
            log.info("[FCM] ADC 로 들어옴");
            var options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .build();
            return initOnce(options);
        } catch (Exception ignore) {}

        // 파일 경로 ( 로컬 환경 )
        if (serviceAccountPath != null && serviceAccountPath.exists()) {
            log.info("[FCM] file path로 들어옴: {}", serviceAccountPath);
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
    private FirebaseApp initFromStream(InputStream in) throws Exception {
        var options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(in))
                .build();
        return initOnce(options);
    }

    private FirebaseApp initOnce(FirebaseOptions options) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.info("[FCM] FirebaseApp 초기화 되었습니다.");
            return FirebaseApp.initializeApp(options);
        }
        log.info("[FCM] FirebaseApp 이미 존재합니다");
        return FirebaseApp.getInstance();
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp app) {
        return FirebaseMessaging.getInstance(app);
    }
}
