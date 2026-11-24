package io.github.xiaomisum.robotest.service.ai.config;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 失败原因提取（详设总册 4.5）：真实原因、根因兜底、脱敏与截断。 */
class AiFailureReasonTest {

    @Test
    void of_topLevelReason_preservesIt() {
        assertEquals("401 unauthorized", AiFailureReason.of(new RuntimeException("401 unauthorized")));
    }

    @Test
    void of_genericTopWithRealCause_usesRootMessage() {
        RuntimeException wrapper = new RuntimeException(ErrorCodeConstants.AI_MODEL_CALL_FAILED.msg(),
                new java.net.SocketTimeoutException("connect timed out"));

        assertEquals("connect timed out", AiFailureReason.of(wrapper));
    }

    @Test
    void of_distinctTopAndRoot_combinesBoth() {
        RuntimeException outer = new RuntimeException("模型返回异常", new IllegalArgumentException("quota exceeded"));

        assertEquals("模型返回异常: quota exceeded", AiFailureReason.of(outer));
    }

    @Test
    void of_sameTopAndRoot_keepsSingleMessage() {
        RuntimeException outer = new RuntimeException("connect timed out",
                new IllegalStateException("connect timed out"));

        assertEquals("connect timed out", AiFailureReason.of(outer));
    }

    @Test
    void of_credentials_areMasked() {
        String raw = "Bearer sk-abcdefghijklmnop failed; apiKey=sk-1234567890, token: plainSecret99";

        String reason = AiFailureReason.of(new RuntimeException(raw));

        assertFalse(reason.contains("sk-abcdefghijklmnop"));
        assertFalse(reason.contains("sk-1234567890"));
        assertFalse(reason.contains("plainSecret99"));
        assertTrue(reason.contains("***"));
    }

    @Test
    void of_exceedingLimit_truncates() {
        assertEquals("0123456789", AiFailureReason.of(new RuntimeException("0123456789abcdef"), 10));
    }

    @Test
    void of_genericOnly_fallsBackToExceptionType() {
        RuntimeException generic = new RuntimeException(ErrorCodeConstants.AI_MODEL_CALL_FAILED.msg());

        assertEquals("RuntimeException", AiFailureReason.of(generic));
    }

    @Test
    void of_withoutMessage_fallsBackToExceptionType() {
        assertEquals("IllegalStateException", AiFailureReason.of(new IllegalStateException()));
    }

    @Test
    void of_null_returnsEmpty() {
        assertEquals("", AiFailureReason.of(null));
    }
}
