package io.github.xiaomisum.robotest.framework.security;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 敏感字段递归脱敏（安全规范 6.4/8.3）：审计、访问日志与错误日志共用 */
class SensitiveDataMaskerTest {

    @Test
    void isSensitiveKeyMatchesCredentialNames() {
        assertTrue(SensitiveDataMasker.isSensitiveKey("password"));
        assertTrue(SensitiveDataMasker.isSensitiveKey("newPassword"));
        assertTrue(SensitiveDataMasker.isSensitiveKey("accessToken"));
        assertTrue(SensitiveDataMasker.isSensitiveKey("X-Api-Key"));
        assertTrue(SensitiveDataMasker.isSensitiveKey("Authorization"));
        assertTrue(SensitiveDataMasker.isSensitiveKey("cookie"));
        assertFalse(SensitiveDataMasker.isSensitiveKey("username"));
        assertFalse(SensitiveDataMasker.isSensitiveKey(null));
    }

    @Test
    void sanitizeMapMasksNestedValuesAtAnyDepth() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("user", Map.of("name", "u", "password", "p@ss"));
        nested.put("items", List.of(Map.of("token", "abc"), "plain"));
        nested.put("refresh_token", "r1");

        Map<String, Object> out = SensitiveDataMasker.sanitizeMap(nested);

        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) out.get("user");
        assertEquals("u", user.get("name"));
        assertEquals(SensitiveDataMasker.MASK, user.get("password"));
        assertEquals(SensitiveDataMasker.MASK, out.get("refresh_token"));
        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) out.get("items");
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) items.get(0);
        assertEquals(SensitiveDataMasker.MASK, first.get("token"));
        assertEquals("plain", items.get(1));
    }

    @Test
    void sanitizeMapMasksNameValueHeaderStyleEntries() {
        Map<String, Object> headerItem = new LinkedHashMap<>();
        headerItem.put("key", "Authorization");
        headerItem.put("value", "Bearer secret-jwt");
        Map<String, Object> plainItem = new LinkedHashMap<>();
        plainItem.put("key", "Content-Type");
        plainItem.put("value", "application/json");

        Map<String, Object> maskedAuth = SensitiveDataMasker.sanitizeMap(headerItem);
        Map<String, Object> maskedPlain = SensitiveDataMasker.sanitizeMap(plainItem);

        assertEquals(SensitiveDataMasker.MASK, maskedAuth.get("value"));
        assertEquals("application/json", maskedPlain.get("value"));
    }

    @Test
    void sanitizeValueMasksInlineCredentialsInStrings() {
        String jdbc = "jdbc:postgresql://db:5432/app?user=app&password=realpw&ssl=true";
        String sanitized = (String) SensitiveDataMasker.sanitizeValue(jdbc);

        assertFalse(sanitized.contains("realpw"));
        assertTrue(sanitized.contains("password=" + SensitiveDataMasker.MASK));
        assertTrue(sanitized.contains("user=app"));
    }

    @Test
    void sanitizeHeadersMasksOnlySensitiveHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("authorization", "Bearer t");
        headers.put("x-api-key", "k");
        headers.put("content-type", "application/json");

        Map<String, String> out = SensitiveDataMasker.sanitizeHeaders(headers);

        assertEquals(SensitiveDataMasker.MASK, out.get("authorization"));
        assertEquals(SensitiveDataMasker.MASK, out.get("x-api-key"));
        assertEquals("application/json", out.get("content-type"));
    }

    @Test
    void sanitizeErrorParamsMasksQueryAndObjectBody() {
        String raw = "{\"query\":{\"pageNo\":\"1\",\"access_token\":\"tok\"},"
                + "\"body\":\"{\\\"oldPassword\\\":\\\"a\\\",\\\"newPassword\\\":\\\"b\\\",\\\"age\\\":3}\"}";

        String sanitized = SensitiveDataMasker.sanitizeErrorParams(raw);

        assertFalse(sanitized.contains("\"tok\""));
        assertFalse(sanitized.contains("\"a\""));
        assertTrue(sanitized.contains("\"pageNo\":\"1\"") || sanitized.contains("\"pageNo\": \"1\""));
        assertTrue(sanitized.contains("age"));
    }

    @Test
    void sanitizeErrorParamsMasksNonObjectBodyWholesale() {
        String raw = "{\"query\":{},\"body\":\"username=alice&password=realpw\"}";

        String sanitized = SensitiveDataMasker.sanitizeErrorParams(raw);

        assertFalse(sanitized.contains("realpw"));
    }

    @Test
    void sanitizeErrorParamsFailsSafeOnUnparsableInput() {
        assertEquals(SensitiveDataMasker.MASK, SensitiveDataMasker.sanitizeErrorParams("not-json"));
        assertEquals("", SensitiveDataMasker.sanitizeErrorParams(""));
    }
}
