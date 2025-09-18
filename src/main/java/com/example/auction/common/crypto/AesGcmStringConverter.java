package com.example.auction.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/*
* AES-GCM 을 사용한 토큰 암호화 / 해시
* 매번 암호화 된 형태로 제공
* */
@Converter
@Slf4j
public class AesGcmStringConverter implements AttributeConverter<String, String> {
    // 키 회전 발생 대비 시 버전 프리픽스
    private static final String PREFIX = "v1:";
    private static final int IV_LEN = 12;
    private static final SecretKey KEY = CryptoKeys.currentKey();

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        // 재식별 방지를 위한 랜덤 IV 설정
        byte[] iv = Crypto.randomIV12();
        byte[] enc = Crypto.aesGcmEncrypt(KEY, iv, Crypto.utf8(attribute));
        byte[] both = new byte[IV_LEN + enc.length];
        System.arraycopy(iv, 0, both, 0, IV_LEN);
        System.arraycopy(enc, 0, both, IV_LEN, enc.length);
        // v1:<base64(iv|ciphertext|tag)> 형태 저장
        return PREFIX + Base64.getEncoder().encodeToString(both);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        String payload = dbData.startsWith(PREFIX) ? dbData.substring(PREFIX.length()) : dbData;
        byte[] raw = Base64.getDecoder().decode(payload);
        byte[] iv = Arrays.copyOfRange(raw, 0, IV_LEN);
        byte[] enc = Arrays.copyOfRange(raw, IV_LEN, raw.length);
        // 복호화 실패 시 예외
        return new String(Crypto.aesGcmDecrypt(KEY, iv, enc), StandardCharsets.UTF_8);
    }
}
