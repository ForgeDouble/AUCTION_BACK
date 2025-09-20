package com.example.auction.common.crypto;


import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/*
* AES-GCM 을 사용하면 매번 랜덤 암호이기 때문에 HMAC 해시 컬럼으로 조회/유니크/삭제 처리 기능
* 검색결과 해시 -> SERVER SECRET KEY 없이 역산 불가
* */
public final class TokenHash {
    private TokenHash() {}
    private static byte[] key() {
        String b64 = Optional.ofNullable(System.getenv("TOKEN_HASH_KEY_B64"))
                .orElse(System.getProperty("TOKEN_HASH_KEY_B64"));
        if (b64 == null || b64.isBlank()) throw new IllegalStateException("TOKEN_HASH_KEY_B64 not set");
        return Base64.getDecoder().decode(b64);
    }

    public static String hmacSha256B64(String tokenPlain) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key(), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(
                    mac.doFinal(tokenPlain.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 계산이 실패하였습니다 ", e);
        }
    }
}
