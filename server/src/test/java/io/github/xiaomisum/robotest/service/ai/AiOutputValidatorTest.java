package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.service.ai.support.AiOutputValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiOutputValidatorTest {

    @Test
    void stripNoise_removesThinkAndFences() {
        String raw = "<think>推理过程</think>```json\n{\"a\":1}\n```";
        assertEquals("{\"a\":1}", AiOutputValidator.stripNoise(raw));
    }

    @Test
    void stripNoise_caseInsensitiveThink() {
        String raw = "<THINK>x</THINK>{\"a\":1}";
        assertEquals("{\"a\":1}", AiOutputValidator.stripNoise(raw));
    }

    @Test
    void extractJson_objectFromSurroundingText() {
        String raw = "这是结果：{\"title\":\"登录\",\"count\":3} 以上。";
        assertEquals("{\"title\":\"登录\",\"count\":3}", AiOutputValidator.extractJson(raw));
    }

    @Test
    void extractJson_array() {
        assertEquals("[1,2,3]", AiOutputValidator.extractJson("前缀 [1,2,3]"));
    }

    @Test
    void extractJson_ignoresBracesInStringLiteral() {
        String raw = "{\"text\":\"包含 } 和 { 的字符串\",\"n\":1}";
        assertEquals(raw, AiOutputValidator.extractJson(raw));
    }

    @Test
    void extractJson_nestedObject() {
        String raw = "{\"outer\":{\"inner\":[1,2]},\"k\":\"v\"}";
        assertEquals(raw, AiOutputValidator.extractJson("```\n" + raw + "\n```"));
    }

    @Test
    void extractJson_noJsonReturnsNull() {
        assertNull(AiOutputValidator.extractJson("纯文本没有 JSON"));
        assertNull(AiOutputValidator.extractJson(""));
        assertNull(AiOutputValidator.extractJson(null));
    }

    @Test
    void extractJson_unclosedReturnsNull() {
        assertNull(AiOutputValidator.extractJson("{\"a\":1"));
    }

    @Test
    void extractJson_handlesEscapedQuote() {
        String raw = "{\"text\":\"引号\\\"内嵌\\\"\",\"n\":1}";
        assertEquals(raw, AiOutputValidator.extractJson(raw));
    }

    @Test
    void extractJson_takesFirstCompleteObject() {
        String result = AiOutputValidator.extractJson("{\"a\":1}{\"b\":2}");
        assertEquals("{\"a\":1}", result);
        assertTrue(result.startsWith("{") && result.endsWith("}"));
    }
}
