package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.service.ai.provider.PromptAssembler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AiTextUtils.truncateToTokenBudget 纯函数单测：估算口径与 PromptAssembler 一致
 * （ASCII 4 字符 ≈ 1 token，其余字符 1 字 ≈ 1 token），超出预算裁剪到最近位置并追加省略号。
 */
class AiTextUtilsTest {

    @Test
    void truncate_nullReturnsNull() {
        assertNull(AiTextUtils.truncateToTokenBudget(null, 100));
    }

    @Test
    void truncate_withinBudgetReturnsOriginal() {
        assertEquals("hello world", AiTextUtils.truncateToTokenBudget("hello world", 100));
        assertEquals("", AiTextUtils.truncateToTokenBudget("", 100));
    }

    @Test
    void truncate_asciiOnly_cutsAtBudgetBoundary() {
        // "abcdefghij" = 10 ascii → 10/4 = 2 token，预算 1 需裁剪
        // ascii 按 4 字符 1 token 向下取整：7 个 ascii 估算 = 1 token，恰好留在预算内
        String result = AiTextUtils.truncateToTokenBudget("abcdefghij", 1);
        assertTrue(result.endsWith("…"));
        // 裁剪后的正文（去省略号）必须 ≤ 预算
        assertTrue(PromptAssembler.estimateTokens(result.substring(0, result.length() - 1)) <= 1);
        assertEquals("abcdefg…", result);
    }

    @Test
    void truncate_asciiWithinBudgetNoEllipsis() {
        // "abcdefgh" = 8 ascii → 8/4 = 2 token，预算 2 恰好放得下
        assertEquals("abcdefgh", AiTextUtils.truncateToTokenBudget("abcdefgh", 2));
    }

    @Test
    void truncate_chinese_oneCharOneToken() {
        // 4 个中文 = 4 token，预算 2 只留 2 字
        assertEquals("你好…", AiTextUtils.truncateToTokenBudget("你好世界", 2));
    }

    @Test
    void truncate_mixed_asciiAndChinese() {
        // "ab" (2 ascii → 0 token) + "你好" (2 token) = 2 token，预算 1 只留 1 个中文
        assertEquals("ab你…", AiTextUtils.truncateToTokenBudget("ab你好cd", 1));
    }

    @Test
    void truncate_boundary_asciiQuarterRounding() {
        // 预算 1 时第 5 个 ascii 字符（5/4=1）恰好够，不需裁剪
        assertEquals("abcde", AiTextUtils.truncateToTokenBudget("abcde", 1));
    }
}
