package com.example.auction.common.util;

import org.springframework.stereotype.Component;

import java.util.UUID;

/*  s3 접근하는지 알려주는 정보 */
@Component
public class S3KeyUtil {

    // 유저 이미지 관련
    public String userAvatarKey(Long userId, String ext) {
        return "users/%d/avatar/%s%s".formatted(userId, UUID.randomUUID(), ext);
    }

    // 상품 이미지 관련
    public String productImageKey(Long productId, String ext) {
        return "products/%d/images/%s%s".formatted(productId, UUID.randomUUID(), ext);
    }
}
