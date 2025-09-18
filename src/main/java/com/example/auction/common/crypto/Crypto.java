package com.example.auction.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

public final class Crypto {
    private Crypto() {}

    public static final int GCM_TAG_BITS = 128; // 16 bytes

    public static byte[] aesGcmEncrypt(SecretKey key, byte[] iv, byte[] plain) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(plain);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM 암호화에 실패했습니다", e);
        }
    }

    public static byte[] aesGcmDecrypt(SecretKey key, byte[] iv, byte[] cipherBytes) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(cipherBytes);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM 해독에 실패했습니다", e);
        }
    }

    public static byte[] randomIV12() {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    public static byte[] utf8(String s) {
        return s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8);
    }
}
