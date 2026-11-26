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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void defaultPrompt_singleModuleKeepsWholeDocumentAndEmitsModuleFields() {
        RequirementImportTypeAdapter adapter = new RequirementImportTypeAdapter();
        String prompt = adapter.defaultPrompt();
        assertTrue(prompt.contains("只产出一条建议"));
        assertTrue(prompt.contains("与文档原文全文完全一致"));
        assertTrue(prompt.contains("moduleName"));
        assertTrue(prompt.contains("newModule"));
        assertTrue(prompt.contains("只输出一个 JSON 对象"));
        assertTrue(prompt.contains("禁止输出任何解释、分析、思考过程"));
        assertTrue(adapter.jsonMode());
        assertTrue(adapter.outputRepairHint().contains("只输出一个 JSON 对象"));
    }
}
