package io.github.xiaomisum.robotest.service.ai.support;

import io.github.xiaomisum.robotest.service.ai.provider.PromptAssembler;
import org.junit.jupiter.api.Test;

import java.util.Set;

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

    @Test
    void tokenizeKeywordsForClustering_nullAndBlank_empty() {
        assertTrue(AiTextUtils.tokenizeKeywordsForClustering(null).isEmpty());
        assertTrue(AiTextUtils.tokenizeKeywordsForClustering("   ").isEmpty());
    }

    @Test
    void tokenizeKeywordsForClustering_cjkEachCharIsToken() {
        // 中文标题无空格分隔，整句切分会使重叠系数恒为 0 → 按单字切分（最小语义单元）
        assertEquals(Set.of("登", "录", "按", "钮", "无", "响", "应"),
                AiTextUtils.tokenizeKeywordsForClustering("登录按钮无响应"));
    }

    @Test
    void tokenizeKeywordsForClustering_nonCjkWholeWord() {
        // 非 CJK 字母数字按整词保留，过滤单字符噪声（与查重降级口径一致）
        assertEquals(Set.of("excel", "列", "顺", "序"),
                AiTextUtils.tokenizeKeywordsForClustering("Excel 列顺序"));
        assertEquals(Set.of("api"), AiTextUtils.tokenizeKeywordsForClustering("a api"));
    }

    @Test
    void tokenizeKeywordsForClustering_mixed_cjkAndWord() {
        // CJK 单字 + 数字整词并存
        assertEquals(Set.of("登", "录", "1366x768", "按", "钮"),
                AiTextUtils.tokenizeKeywordsForClustering("登录 1366x768 按钮"));
    }

    @Test
    void tokenizeKeywordsForClustering_deduplicatesAndKeepsOrder() {
        assertEquals(Set.of("登", "录", "页", "面"), AiTextUtils.tokenizeKeywordsForClustering("登录登录页面"));
    }
}
