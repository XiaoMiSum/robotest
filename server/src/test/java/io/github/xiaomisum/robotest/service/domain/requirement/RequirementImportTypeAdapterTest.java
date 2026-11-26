package io.github.xiaomisum.robotest.service.domain.requirement;

import io.github.xiaomisum.robotest.model.entity.file.FileResource;
import io.github.xiaomisum.robotest.repository.file.FileResourceMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiSettingsReader;
import io.github.xiaomisum.robotest.service.ai.task.type.AgentExecutionPreparation;
import io.github.xiaomisum.robotest.service.ai.task.type.ProposalDraft;
import io.github.xiaomisum.robotest.service.domain.file.FileContent;
import io.github.xiaomisum.robotest.service.domain.file.FileResourceService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequirementImportTypeAdapterTest {

    @Test
    void prepare_imageBuildsVisionMediaAndContextAwareParsers() {
        UUID fileId = UUID.randomUUID();
        FileResourceMapper fileMapper = mock(FileResourceMapper.class);
        FileResourceService fileService = mock(FileResourceService.class);
        ProjectModuleMapper moduleMapper = mock(ProjectModuleMapper.class);
        RequirementImportTypeAdapter support = new RequirementImportTypeAdapter();
        ReflectionTestUtils.setField(support, "fileResourceMapper", fileMapper);
        ReflectionTestUtils.setField(support, "fileResourceService", fileService);
        ReflectionTestUtils.setField(support, "projectModuleMapper", moduleMapper);
        FileResource row = new FileResource();
        row.setFileName("prd.png");
        when(fileMapper.selectById(fileId)).thenReturn(row);
        when(fileService.readBytes(fileId)).thenReturn(new FileContent(new byte[]{1, 2}, "image/png", "prd.png"));
        when(moduleMapper.listByProjectId(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        Map<String, Object> input = Map.of("fileId", fileId.toString());

        assertEquals(AiSettingsReader.CAPABILITY_VISION, support.requiredCapability(input));
        AgentExecutionPreparation prepared = support.prepare(input, UUID.randomUUID());
        assertEquals(1, prepared.media().size());
        assertEquals(1, prepared.proposalParser().apply(
                "{\"documentMeta\":{\"detectedVersion\":\"V2\"},\"artifacts\":[{\"content\":{\"title\":\"登录\",\"priority\":\"high\"}}]}")
                .size());
        assertEquals("V2", ((Map<?, ?>) prepared.resultMetaParser().apply(
                "{\"documentMeta\":{\"detectedVersion\":\"V2\"}}").get("documentMeta"))
                .get("detectedVersion"));
        List<?> newModuleArtifacts = prepared.proposalParser().apply(
                "{\"artifacts\":[{\"content\":{\"title\":\"订单\",\"moduleName\":\"订单模块\",\"newModule\":true,\"priority\":\"high\"}}]}");
        Map<?, ?> content = ((ProposalDraft) newModuleArtifacts.get(0)).content();
        assertEquals("订单模块", content.get("moduleName"));
        assertEquals(true, content.get("newModule"));
        assertNull(content.get("moduleId"));
    }

    @Test
    void defaultPrompt_delegatesSplitDecisionToModelAndUsesSourceReferences() {
        RequirementImportTypeAdapter adapter = new RequirementImportTypeAdapter();
        String prompt = adapter.defaultPrompt();
        assertTrue(prompt.contains("需求数量由你判断"));
        assertTrue(prompt.contains("@blocks:B2,B5,B6"));
        assertTrue(prompt.contains("公共前言"));
        assertFalse(prompt.contains("与文档原文全文完全一致"));
        assertFalse(prompt.contains("拆分规则（模块是唯一边界）"));
        assertTrue(prompt.contains("moduleName"));
        assertTrue(prompt.contains("newModule"));
        assertTrue(prompt.contains("只输出一个 JSON 对象"));
        assertTrue(prompt.contains("禁止输出任何解释、分析、思考过程"));
        assertTrue(adapter.jsonMode());
        assertTrue(adapter.outputRepairHint().contains("只输出一个 JSON 对象"));
        assertTrue(adapter.outputRepairHint().contains("@full"));
    }

    @Test
    void prepare_textBuildsBlockMarkersAndFillsDescriptionsFromSource() {
        UUID fileId = UUID.randomUUID();
        String document = "公共前言\n# 1.1.1 测试用例管理\n用例正文\n# 1.1.2 测试评审管理\n评审正文";
        FileResourceMapper fileMapper = mock(FileResourceMapper.class);
        FileResourceService fileService = mock(FileResourceService.class);
        ProjectModuleMapper moduleMapper = mock(ProjectModuleMapper.class);
        RequirementImportTypeAdapter adapter = new RequirementImportTypeAdapter();
        ReflectionTestUtils.setField(adapter, "fileResourceMapper", fileMapper);
        ReflectionTestUtils.setField(adapter, "fileResourceService", fileService);
        ReflectionTestUtils.setField(adapter, "projectModuleMapper", moduleMapper);
        FileResource row = new FileResource();
        row.setFileName("prd.md");
        when(fileMapper.selectById(fileId)).thenReturn(row);
        when(fileService.readBytes(fileId)).thenReturn(new FileContent(document.getBytes(StandardCharsets.UTF_8),
                "text/markdown", "prd.md"));
        when(moduleMapper.listByProjectId(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        AgentExecutionPreparation prepared = adapter.prepare(Map.of("fileId", fileId.toString()), UUID.randomUUID());
        String marked = prepared.promptVariables().get("documentText");
        assertTrue(marked.contains("[B1]\n公共前言"));
        assertTrue(marked.contains("[B2]\n# 1.1.1 测试用例管理"));
        assertFalse(prepared.inputSnapshot().contains("用例正文"));

        String output = """
                {"artifacts":[
                  {"content":{"title":"用例","description":"@blocks:B2"}},
                  {"content":{"title":"评审","description":"@blocks:B3"}}
                ]}""";
        List<?> drafts = prepared.proposalParser().apply(output);
        assertEquals(2, drafts.size());
        Map<?, ?> first = ((ProposalDraft) drafts.get(0)).content();
        Map<?, ?> second = ((ProposalDraft) drafts.get(1)).content();
        assertEquals("公共前言\n# 1.1.1 测试用例管理\n用例正文\n", first.get("description"));
        assertEquals("公共前言\n# 1.1.2 测试评审管理\n评审正文", second.get("description"));
    }
}
