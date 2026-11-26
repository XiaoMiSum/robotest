package io.github.xiaomisum.robotest.service.domain.requirement;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import org.junit.jupiter.api.Test;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementSuggestionParserTest {

    @Test
    void parseJsonObject_plainJsonObject_parses() {
        Map<String, Object> parsed = RequirementSuggestionParser.parseJsonObject(
                "{\"documentMeta\":{\"detectedVersion\":\"V2\"},\"artifacts\":[]}");

        assertEquals("V2", ((Map<?, ?>) parsed.get("documentMeta")).get("detectedVersion"));
    }

    @Test
    void parseJsonObject_codeFenceOnly_parses() {
        String output = "```json\n{\"documentMeta\":{\"detectedVersion\":\"V2\"}}\n```";

        assertEquals("V2", ((Map<?, ?>) RequirementSuggestionParser.parseJsonObject(output)
                .get("documentMeta")).get("detectedVersion"));
    }

    @Test
    void parseJsonObject_proseBeforeCodeFence_extractsJsonObject() {
        String output = "我来分析这份需求文档。文档标题为\"软件测试平台\"，整体归属一个模块。\n\n"
                + "```json\n{\"documentMeta\":{\"detectedVersion\":\"V1.0\"},\"artifacts\":[]}\n```";

        Map<String, Object> parsed = RequirementSuggestionParser.parseJsonObject(output);

        assertEquals("V1.0", ((Map<?, ?>) parsed.get("documentMeta")).get("detectedVersion"));
    }

    @Test
    void parseJsonObject_braceInProseBeforeJson_skipsFalseCandidate() {
        String output = "说明 {非 JSON 片段}，正文如下：\n"
                + "{\"documentMeta\":{\"versionEvidence\":\"原文含 { 括号 } 引语\"},\"artifacts\":[]}";

        Map<String, Object> parsed = RequirementSuggestionParser.parseJsonObject(output);

        Map<?, ?> meta = (Map<?, ?>) parsed.get("documentMeta");
        assertEquals("原文含 { 括号 } 引语", meta.get("versionEvidence"));
    }

    @Test
    void parseJsonObject_nonJsonText_throwsModelCallFailed() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> RequirementSuggestionParser.parseJsonObject("仅有一段分析文字，没有 JSON"));

        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), exception.getCode());
        assertTrue(exception.getMessage().contains("模型输出无法解析为 JSON"));
    }
}
