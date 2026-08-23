package io.github.xiaomisum.robotest.service.ai.support;

import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;

/**
 * AI 服务密钥加解密工具（AES-256-GCM），算法实现已抽取至 {@link SecretCryptoUtil} 供多域共用，
 * 本类保留原 API 以维持 AI 域调用点稳定。
 */
public final class AiCryptoUtil {

    private AiCryptoUtil() {
    }

    public static byte[] parseKey(String base64Key) {
        return SecretCryptoUtil.parseKey(base64Key);
    }

    public static String encrypt(byte[] key, String plainText) {
        return SecretCryptoUtil.encrypt(key, plainText);
    }

    public static String decrypt(byte[] key, String cipherText) {
        return SecretCryptoUtil.decrypt(key, cipherText);
    }

    public static String keySuffix(String plainKey) {
        return SecretCryptoUtil.keySuffix(plainKey);
    }
}
