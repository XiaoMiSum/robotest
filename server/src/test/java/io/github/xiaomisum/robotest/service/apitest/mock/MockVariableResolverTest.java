package io.github.xiaomisum.robotest.service.apitest.mock;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockVariableResolverTest {

    @Test
    void nullAndPlainInputPassThrough() {
        assertNull(MockVariableResolver.resolve(null));
        assertSame("plain", MockVariableResolver.resolve("plain"));
        // 不含 ${ 时返回原引用，避免无谓拷贝
        assertEquals("hello world", MockVariableResolver.resolve("hello world"));
    }

    @Test
    void uuidPlaceholderResolvesToValidUuid() {
        String resolved = MockVariableResolver.resolve("${uuid()}");
        assertDoesNotContainPlaceholder(resolved);
        UUID.fromString(resolved);
    }

    @Test
    void timestampPlaceholderResolvesToNumeric() {
        String resolved = MockVariableResolver.resolve("${timestamp()}");
        Long.parseLong(resolved);
    }

    @Test
    void existingEnvVariableResolves() {
        // PATH 在 Windows/Linux 测试环境均存在
        String resolved = MockVariableResolver.resolve("prefix-${env:PATH}-suffix");
        assertDoesNotContainPlaceholder(resolved);
        assertTrue(resolved.startsWith("prefix-"));
        assertTrue(resolved.endsWith("-suffix"));
    }

    @Test
    void undefinedPlaceholdersPreserved() {
        String input = "a=${nope},b=${env:__DEFINITELY_NOT_SET__}";
        assertEquals(input, MockVariableResolver.resolve(input));
    }

    @Test
    void mixedContentResolvedInPlace() {
        String first = MockVariableResolver.resolve("${uuid()}-${uuid()}");
        String second = MockVariableResolver.resolve("${uuid()}-${uuid()}");
        assertDoesNotContainPlaceholder(first);
        assertNotEquals(first, second);
    }

    private static void assertDoesNotContainPlaceholder(String value) {
        org.junit.jupiter.api.Assertions.assertFalse(value.contains("${"), "占位符未解析: " + value);
    }

}
