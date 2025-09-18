package com.example.auction.common.crypto;


import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class TokenHash {
    private TokenHash() {}

    private static byte[] key() {
        String b64 = System.getenv("TOKEN_HASH_KEY_B64");
        if (b64 == null || b64.isBlank()) b64 = System.getProperty("TOKEN_HASH_KEY_B64");
        if (b64 == null || b64.isBlank()) throw new IllegalStateException("TOKEN_HASH_KEY_B64 이 설정되지 않았습니다");
        return Base64.getDecoder().decode(b64);
    }

    public static String hmacSha256B64(String tokenPlain) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key(), "HmacSHA256"));
            byte[] out = mac.doFinal(tokenPlain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 계산에 실패했습니다", e);
        }
    }
}
