package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.service.ai.provider.PromptAssembler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptAssemblerTest {

    @Test
    void estimateTokens_emptyAndNull() {
        assertEquals(0, PromptAssembler.estimateTokens(null));
        assertEquals(0, PromptAssembler.estimateTokens(""));
    }

    @Test
    void estimateTokens_asciiFourCharsPerToken() {
        // 8 个 ASCII 字符 ≈ 2 token
        assertEquals(2, PromptAssembler.estimateTokens("abcdefgh"));
    }

    @Test
    void estimateTokens_chineseOneCharPerToken() {
        // 中文 1 字 ≈ 1 token
        assertEquals(4, PromptAssembler.estimateTokens("测试用例"));
    }

    @Test
    void estimateTokens_mixed() {
        // 4 中文(4) + 4 ASCII(1) = 5
        assertEquals(5, PromptAssembler.estimateTokens("测试用例abcd"));
    }

    @Test
    void estimateTokens_budgetBoundary() {
        // 预算常量存在且为正
        assertTrue(PromptAssembler.INPUT_TOKEN_BUDGET > 0);
    }
}
