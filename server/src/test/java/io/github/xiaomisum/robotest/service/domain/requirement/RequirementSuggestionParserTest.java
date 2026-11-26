package io.github.xiaomisum.robotest.service.domain.requirement;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import org.junit.jupiter.api.Test;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.List;
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

    @Test
    void splitDocumentBlocks_markedTextReferencesBlocksAndConcatenationRestoresOriginal() {
        String document = "公共前言\n# 1.1.1 测试用例管理\n用例正文\n# 1.1.2 测试评审管理\n评审正文";

        List<String> blocks = RequirementSuggestionParser.splitDocumentBlocks(document);

        assertEquals(3, blocks.size());
        assertEquals(document, String.join("", blocks));
        assertTrue(RequirementSuggestionParser.markBlocks(blocks).contains("[B2]\n# 1.1.1 测试用例管理"));
    }

    @Test
    void sanitizeArtifacts_fullReference_fillsEntireDocument() {
        String document = "# 需求\n正文";
        List<String> blocks = RequirementSuggestionParser.splitDocumentBlocks(document);
        Map<String, Object> parsed = RequirementSuggestionParser.parseJsonObject(
                "{\"artifacts\":[{\"content\":{\"title\":\"需求\",\"description\":\"@full\"}}]}");

        Map<?, ?> content = (Map<?, ?>) RequirementSuggestionParser.sanitizeArtifacts(parsed, List.of(), blocks)
                .get(0).get("content");

        assertEquals(document, content.get("description"));
    }

    @Test
    void sanitizeArtifacts_multipleReferences_prependsUnclaimedPreambleToEach() {
        String document = "公共前言\n# 1.1.1 测试用例管理\n用例正文\n# 1.1.2 测试评审管理\n评审正文";
        List<String> blocks = RequirementSuggestionParser.splitDocumentBlocks(document);
        String output = """
                {"artifacts":[
                  {"content":{"title":"用例","description":"@blocks:B2"}},
                  {"content":{"title":"评审","description":"@blocks:B3"}}
                ]}""";

        List<Map<String, Object>> artifacts = RequirementSuggestionParser.sanitizeArtifacts(
                RequirementSuggestionParser.parseJsonObject(output), List.of(), blocks);
        Map<?, ?> first = (Map<?, ?>) artifacts.get(0).get("content");
        Map<?, ?> second = (Map<?, ?>) artifacts.get(1).get("content");

        assertEquals("公共前言\n# 1.1.1 测试用例管理\n用例正文\n", first.get("description"));
        assertEquals("公共前言\n# 1.1.2 测试评审管理\n评审正文", second.get("description"));
    }

    @Test
    void sanitizeArtifacts_allReferencesInvalid_fallsBackToFullDocument() {
        String document = "# 需求\n正文";
        List<String> blocks = RequirementSuggestionParser.splitDocumentBlocks(document);
        Map<String, Object> parsed = RequirementSuggestionParser.parseJsonObject(
                "{\"artifacts\":[{\"content\":{\"title\":\"一\",\"description\":\"@blocks:B99\"}},"
                        + "{\"content\":{\"title\":\"二\",\"description\":\"模型回显的正文\"}}]}");

        List<Map<String, Object>> artifacts = RequirementSuggestionParser.sanitizeArtifacts(parsed, List.of(), blocks);

        assertEquals(document, ((Map<?, ?>) artifacts.get(0).get("content")).get("description"));
        assertEquals(document, ((Map<?, ?>) artifacts.get(1).get("content")).get("description"));
    }

    @Test
    void sanitizeArtifacts_withoutSourceBlocks_keepsModelDescription() {
        Map<String, Object> parsed = RequirementSuggestionParser.parseJsonObject(
                "{\"artifacts\":[{\"content\":{\"title\":\"图片需求\",\"description\":\"图片正文\"}}]}");

        Map<?, ?> content = (Map<?, ?>) RequirementSuggestionParser.sanitizeArtifacts(parsed, List.of())
                .get(0).get("content");

        assertEquals("图片正文", content.get("description"));
    }
}
