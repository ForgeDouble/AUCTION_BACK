package com.example.auction.common.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URLConnection;

@Service
@RequiredArgsConstructor
public class S3ObjectService {

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${storage.public-base-url:}")
    private String publicBaseUrl;

    private final S3Client s3;

    // S3에 스트리밍 업로드
    // 추가 - 파일의 확장자가 없는 경우 이를 해결하기 위한 확장자 추론 형태
    public String put(String key, MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = URLConnection.guessContentTypeFromName(key);
            if (contentType == null) contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(file.getSize())
                .cacheControl("public, max-age=2592000") // 30일
                .build();

        try (var in = file.getInputStream()) {
            s3.putObject(req, RequestBody.fromInputStream(in, file.getSize()));
        }
        return key;
    }

    /* S3 객체 삭제 */
    public void delete(String key) {
        if (key == null || key.isBlank()) return;
        s3.deleteObject(b -> b.bucket(bucket).key(key));
    }

    /* 퍼블릭 URL */
    public String toPublicUrl(String key) {
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            String base = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
            return base + "/" + key;
        }
        return s3.utilities().getUrl(b -> b.bucket(bucket).key(key)).toExternalForm();
    }
}
