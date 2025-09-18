package com.example.auction.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.SecretKey;
import java.util.Arrays;
import java.util.Base64;
@Converter
public class AesGcmStringConverter implements AttributeConverter<String, String> {
    private static final String PREFIX = "v1:";
    private static final int IV_LEN = 12;
    private static final SecretKey KEY = CryptoKeys.currentKey();

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        byte[] iv = Crypto.randomIV12();
        byte[] enc = Crypto.aesGcmEncrypt(KEY, iv, Crypto.utf8(attribute));
        byte[] both = new byte[IV_LEN + enc.length];
        System.arraycopy(iv, 0, both, 0, IV_LEN);
        System.arraycopy(enc, 0, both, IV_LEN, enc.length);
        return PREFIX + Base64.getEncoder().encodeToString(both);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        String payload = dbData.startsWith(PREFIX) ? dbData.substring(PREFIX.length()) : dbData;
        byte[] raw = Base64.getDecoder().decode(payload);
        byte[] iv = Arrays.copyOfRange(raw, 0, IV_LEN);
        byte[] enc = Arrays.copyOfRange(raw, IV_LEN, raw.length);
        return new String(Crypto.aesGcmDecrypt(KEY, iv, enc));
    }
}
