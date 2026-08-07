package io.github.xiaomisum.robotest.service.ai.support;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AI 服务密钥加解密工具（AES-256-GCM）。
 *
 * <p>存储格式：Base64(12字节IV || 密文 || 16字节Tag)，每次加密随机 IV；
 * 加密密钥来自环境变量 AI_SECRET_KEY（Base64 编码 32 字节）。</p>
 */
public final class AiCryptoUtil {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private AiCryptoUtil() {
    }

    /**
     * 解析 Base64 编码的 32 字节加密密钥，非法或长度不符返回 null（AI 能力按未启用降级）
     */
    public static byte[] parseKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            return null;
        }
        try {
            byte[] key = Base64.getDecoder().decode(base64Key.trim());
            return key.length == 32 ? key : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String encrypt(byte[] key, String plainText) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherWithTag = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_LENGTH + cipherWithTag.length];
            System.arraycopy(iv, 0, out, 0, IV_LENGTH);
            System.arraycopy(cipherWithTag, 0, out, IV_LENGTH, cipherWithTag.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("AI secret encrypt failed", e);
        }
    }

    /**
     * 解密失败（密钥轮换/密文损坏）返回 null，由调用方按 AI 未启用降级处理
     */
    public static String decrypt(byte[] key, String cipherText) {
        try {
            byte[] data = Base64.getDecoder().decode(cipherText);
            if (data.length <= IV_LENGTH) {
                return null;
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, data, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(data, IV_LENGTH, data.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 截取密钥末 4 位供管理端脱敏展示，长度不足 4 位时返回全文
     */
    public static String keySuffix(String plainKey) {
        if (plainKey == null || plainKey.isBlank()) {
            return null;
        }
        return plainKey.length() <= 4 ? plainKey : plainKey.substring(plainKey.length() - 4);
    }
}
