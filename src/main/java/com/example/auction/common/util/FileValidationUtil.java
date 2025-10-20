package com.example.auction.common.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/* 파일 확장자 정책 설정 */
@Component
public class FileValidationUtil {
    private final long maxBytes;
    private final Set<String> allowed;

    public FileValidationUtil(
            @Value("${storage.max-image-mb:10}") int maxMb,
            @Value("${storage.allowed-mime:image/jpeg,image/png,image/webp,image/gif}") String allowedCsv) {
        this.maxBytes = (long) maxMb * 1024 * 1024;
        this.allowed = Arrays.stream(allowedCsv.split(","))
                .map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toSet());
    }

    public void ensureImage(MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) throw new IllegalArgumentException("빈 파일입니다.");
        if (multipartFile.getSize() > maxBytes) throw new IllegalArgumentException("파일 크기 초과");
        String ct = Optional.ofNullable(multipartFile.getContentType()).orElse("");
        if (!allowed.contains(ct)) throw new IllegalArgumentException("허용되지 않는 타입: " + ct);
    }

    public String ext(String contentType, String originalFilename) {
        // contentType선 적용 및 파일명으로 확장자 추론
        if ("image/jpeg".equals(contentType)) return ".jpg";
        if ("image/png".equals(contentType))  return ".png";
        if ("image/webp".equals(contentType)) return ".webp";
        if ("image/gif".equals(contentType))  return ".gif";

        if (originalFilename != null && originalFilename.contains(".")) {
            String lower = originalFilename.toLowerCase();
            if (lower.endsWith(".jpeg") || lower.endsWith(".jpg")) return ".jpg";
            if (lower.endsWith(".png"))  return ".png";
            if (lower.endsWith(".webp")) return ".webp";
            if (lower.endsWith(".gif"))  return ".gif";
        }
        return "";
    }
}