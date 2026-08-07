package io.github.xiaomisum.robotest.service.ai.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiCryptoUtilTest {

    // 32 字节全零密钥的 Base64（仅测试用）
    private static final String KEY_BASE64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Test
    void parseKey_validLength() {
        byte[] key = AiCryptoUtil.parseKey(KEY_BASE64);
        assertEquals(32, key.length);
    }

    @Test
    void parseKey_blankOrInvalidReturnsNull() {
        assertNull(AiCryptoUtil.parseKey(null));
        assertNull(AiCryptoUtil.parseKey(""));
        assertNull(AiCryptoUtil.parseKey("not-base64!!!"));
        // 长度不足 32 字节
        assertNull(AiCryptoUtil.parseKey("AAAA"));
    }

    @Test
    void encryptDecrypt_roundTrip() {
        byte[] key = AiCryptoUtil.parseKey(KEY_BASE64);
        String plain = "sk-1234567890abcdef";
        String cipher = AiCryptoUtil.encrypt(key, plain);
        assertNotEquals(plain, cipher);
        assertEquals(plain, AiCryptoUtil.decrypt(key, cipher));
    }

    @Test
    void encrypt_randomIvProducesDifferentCipher() {
        byte[] key = AiCryptoUtil.parseKey(KEY_BASE64);
        String plain = "same-secret";
        // 每次加密随机 IV，密文不同但可解回同一明文
        assertNotEquals(AiCryptoUtil.encrypt(key, plain), AiCryptoUtil.encrypt(key, plain));
    }

    @Test
    void decrypt_corruptedReturnsNull() {
        byte[] key = AiCryptoUtil.parseKey(KEY_BASE64);
        assertNull(AiCryptoUtil.decrypt(key, "not-a-valid-cipher"));
        assertNull(AiCryptoUtil.decrypt(key, "AAAA"));
    }

    @Test
    void decrypt_wrongKeyReturnsNull() {
        byte[] key = AiCryptoUtil.parseKey(KEY_BASE64);
        String cipher = AiCryptoUtil.encrypt(key, "secret");
        byte[] otherKey = AiCryptoUtil.parseKey("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBA=");
        // GCM 认证标签校验失败，密钥轮换/丢失按未启用降级
        assertNull(AiCryptoUtil.decrypt(otherKey, cipher));
    }

    @Test
    void keySuffix_takesLastFour() {
        assertEquals("cdef", AiCryptoUtil.keySuffix("sk-abcdef"));
        assertEquals("12", AiCryptoUtil.keySuffix("12"));
        assertNull(AiCryptoUtil.keySuffix(null));
        assertNull(AiCryptoUtil.keySuffix(""));
    }

    @Test
    void encryptDecrypt_unicodeContent() {
        byte[] key = AiCryptoUtil.parseKey(KEY_BASE64);
        String plain = "密钥-测试-🔑";
        assertEquals(plain, AiCryptoUtil.decrypt(key, AiCryptoUtil.encrypt(key, plain)));
        assertTrue(AiCryptoUtil.encrypt(key, plain).length() > 0);
    }
}
