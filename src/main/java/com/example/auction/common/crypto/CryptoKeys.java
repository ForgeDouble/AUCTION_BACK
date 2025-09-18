package com.example.auction.common.crypto;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public final class CryptoKeys {
    private CryptoKeys() {}

    public static SecretKey currentKey() {
        String b64 = System.getenv("AES_GCM_KEY_B64");
        if (b64 == null || b64.isBlank()) {
            b64 = System.getProperty("AES_GCM_KEY_B64");
        }
        if (b64 == null || b64.isBlank()) {
            throw new IllegalStateException("AES_GCM_KEY_B64 설정되지 않았습니다");
        }
        byte[] raw = Base64.getDecoder().decode(b64);
        if (!(raw.length == 16 || raw.length == 24 || raw.length == 32)) {
            throw new IllegalStateException("AES key must be 16/24/32 bytes (got " + raw.length + ")");
        }
        return new SecretKeySpec(raw, "AES");
    }
}
