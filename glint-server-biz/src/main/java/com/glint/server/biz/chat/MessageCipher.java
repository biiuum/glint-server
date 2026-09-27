package com.glint.server.biz.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class MessageCipher {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String KEY_VERSION = "v1";

    private final SecretKey key;
    private final SecureRandom secureRandom = new SecureRandom();

    public MessageCipher(@Value("${glint.message-encryption-key}") String encodedKey) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("glint.message-encryption-key 必须是 Base64 编码", exception);
        }
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("glint.message-encryption-key 必须解码为 32 字节");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public EncryptedMessage encrypt(String plaintext) {
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedMessage(
                    Base64.getEncoder().encodeToString(ciphertext),
                    Base64.getEncoder().encodeToString(nonce),
                    KEY_VERSION
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法加密消息", exception);
        }
    }

    public String decrypt(EncryptedMessage encryptedMessage) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(
                    TAG_BITS, Base64.getDecoder().decode(encryptedMessage.nonce())
            ));
            return new String(cipher.doFinal(Base64.getDecoder().decode(encryptedMessage.ciphertext())),
                    StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("无法解密消息", exception);
        }
    }

    public record EncryptedMessage(String ciphertext, String nonce, String keyVersion) {
    }
}
