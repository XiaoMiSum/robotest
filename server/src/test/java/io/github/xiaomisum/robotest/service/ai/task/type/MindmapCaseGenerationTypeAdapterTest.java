package io.github.xiaomisum.robotest.service.ai.task.type;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.repository.ai.AiTaskMapper;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.task.TaskSubmitContext;
import io.github.xiaomisum.robotest.service.ai.vector.VectorSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/** 脑图生成用例单测（C8）：归属 / 状态 / 去重校验、快照装配、RAG 降级与提案清洗。 */
@ExtendWith(MockitoExtension.class)
class MindmapCaseGenerationTypeAdapterTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final UUID REQUIREMENT_ID = UUID.randomUUID();
    private static final UUID FOREIGN_ID = UUID.randomUUID();

    @Mock
    private RequirementMapper requirementMapper;
    @Mock
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private AiTaskMapper aiTaskMapper;
    @Mock
    private VectorSearchService vectorSearchService;
    @InjectMocks
    private MindmapCaseGenerationTypeAdapter adapter;

    @Test
    void typeAndPromptRegistration() {
        assertEquals(Constants.AiTaskType.MINDMAP_CASE_GENERATION, adapter.type());
        assertTrue(adapter.systemPrompt().contains("{{requirementContext}}"));
        assertTrue(adapter.toolCodes().isEmpty());
    }

    @Test
    void validateInput_requiresBothIds() {
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(),
                assertThrows(ServiceException.class,
                        () -> adapter.validateInput(Map.of("documentId", DOCUMENT_ID.toString()))).getCode());
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(),
                assertThrows(ServiceException.class,
                        () -> adapter.validateInput(Map.of("requirementId", REQUIREMENT_ID.toString()))).getCode());
    }

    @Test
    void validateInputWithContext_foreignRequirementRejected() {
        when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(requirement(FOREIGN_ID));
        assertEquals(ErrorCodeConstants.MINDMAP_GENERATION_REQUIREMENT_NOT_FOUND.code(),
                assertThrows(ServiceException.class, () -> adapter.validateInput(input(), context())).getCode());
    }

    @Test
    void validateInputWithContext_draftRequirementRejected() {
        when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(requirement(PROJECT_ID, Constants.RequirementStatus.DRAFT));
        assertEquals(ErrorCodeConstants.MINDMAP_GENERATION_REQUIREMENT_NOT_CONFIRMED.code(),
                assertThrows(ServiceException.class, () -> adapter.validateInput(input(), context())).getCode());
    }

    @Test
    void validateInputWithContext_documentOwnershipChecked() {
        when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(requirement(PROJECT_ID));
        TestCaseDocument document = new TestCaseDocument();
        document.setId(DOCUMENT_ID);
        document.setProjectId(FOREIGN_ID);
        when(testCaseDocumentMapper.selectById(DOCUMENT_ID)).thenReturn(document);
        assertEquals(ErrorCodeConstants.MINDMAP_GENERATION_DOCUMENT_NOT_FOUND.code(),
                assertThrows(ServiceException.class, () -> adapter.validateInput(input(), context())).getCode());
    }

    @Test
    void validateInputWithContext_duplicateTaskRejected() {
        when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(requirement(PROJECT_ID));
        when(testCaseDocumentMapper.selectById(DOCUMENT_ID)).thenReturn(document());
        AiTask twin = new AiTask();
        twin.setInput(Map.of("documentId", DOCUMENT_ID.toString(), "requirementId", REQUIREMENT_ID.toString()));
        when(aiTaskMapper.listInProgressByType(PROJECT_ID, MindmapCaseGenerationTypeAdapter.TYPE))
                .thenReturn(List.of(twin));
        assertEquals(ErrorCodeConstants.MINDMAP_GENERATION_DUPLICATE_TASK.code(),
                assertThrows(ServiceException.class, () -> adapter.validateInput(input(), context())).getCode());
    }

    @Test
    void validateInputWithContext_success_freezesRequirementSnapshot() {
        Map<String, Object> input = input();
        Requirement confirmed = requirement(PROJECT_ID);
        confirmed.setCode("REQ-001");
        confirmed.setDescription("支持短信验证码登录");
        when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(confirmed);
        when(testCaseDocumentMapper.selectById(DOCUMENT_ID)).thenReturn(document());
        when(aiTaskMapper.listInProgressByType(PROJECT_ID, MindmapCaseGenerationTypeAdapter.TYPE))
                .thenReturn(List.of());

        adapter.validateInput(input, context());

        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = (Map<String, Object>) input.get("requirementSnapshot");
        assertNotNull(snapshot);
        assertEquals(REQUIREMENT_ID.toString(), snapshot.get("id"));
        assertEquals("REQ-001", snapshot.get("code"));
        assertEquals("支持短信验证码登录", snapshot.get("description"));
    }

    @Test
    void prepare_degradesWhenVectorIndexUnavailable() {
        Map<String, Object> input = input();
        input.put("requirementSnapshot", Map.of(
                "id", REQUIREMENT_ID.toString(), "code", "REQ-001", "title", "短信验证码登录",
                "status", Constants.RequirementStatus.CONFIRMED, "description", "支持短信验证码登录"));
        Requirement confirmed = requirement(PROJECT_ID);
        confirmed.setTitle("短信验证码登录");
        when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(confirmed);
        TestCaseDocument document = document();
        document.setName("登录用例");
        when(testCaseDocumentMapper.selectById(DOCUMENT_ID)).thenReturn(document);
        when(testCaseNodeMapper.listCasesByDocumentIds(anyList())).thenReturn(List.of());
        when(vectorSearchService.search(any(), anyList(), any(), anyInt(), any()))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.AI_VECTOR_INDEX_UNAVAILABLE));

        AgentExecutionPreparation preparation = adapter.prepare(new TaskTypeAdapter.PreparationContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), PROJECT_ID, input, null));

        assertEquals("", preparation.promptVariables().get("ragContext"));
        assertTrue(preparation.promptVariables().get("requirementContext").contains("REQ-001"));
        assertTrue(preparation.promptVariables().get("documentContext").contains("登录用例"));
    }

    @Test
    void proposalParser_sanitizesAttributesAndDropsCasesWithoutValidSource() {
        Requirement confirmed = requirement(PROJECT_ID);
        confirmed.setTitle("短信验证码登录");
        when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(confirmed);
        TestCaseDocument document = document();
        document.setName("登录用例");
        when(testCaseDocumentMapper.selectById(DOCUMENT_ID)).thenReturn(document);
        when(testCaseNodeMapper.listCasesByDocumentIds(anyList())).thenReturn(List.of());
        when(vectorSearchService.search(any(), anyList(), any(), anyInt(), any())).thenReturn(List.of());

        AgentExecutionPreparation preparation = prepare(input());
        String output = """
                {"artifacts":[
                  {"title":"获取验证码","content":{"priority":"P0",
                    "precondition":["用户处于登录页"],"steps":["输入手机号","点击获取验证码","多余步骤"],
                    "expected":["提示验证码已发送","短信送达"],"tags":["登录","验证码"],
                    "sourceRefs":[{"requirementId":"%s","quote":"支持短信验证码"},
                                  {"requirementId":"%s","quote":"越界来源"}]}},
                  {"title":"无来源用例","content":{"priority":"P1","steps":["打开页面"],"expected":["成功"]}}
                ]}
                """.formatted(REQUIREMENT_ID, FOREIGN_ID);

        List<ProposalDraft> drafts = preparation.proposalParser().apply(output);

        assertEquals(1, drafts.size());
        ProposalDraft draft = drafts.get(0);
        assertEquals(Constants.AiProposalKind.MINDMAP_CASE_SUGGESTION, draft.kind());
        assertEquals("获取验证码", draft.title());
        assertEquals(Constants.AiProposalConfirmStatus.PENDING, draft.confirmStatus());
        Map<String, Object> content = draft.content();
        assertEquals("P0", content.get("priority"));
        assertEquals(List.of("用户处于登录页"), content.get("precondition"));
        assertEquals(List.of("输入手机号", "点击获取验证码"), content.get("steps"));
        assertEquals(List.of("提示验证码已发送", "短信送达"), content.get("expected"));
        assertEquals(List.of("登录", "验证码"), content.get("tags"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sourceRefs = (List<Map<String, Object>>) content.get("sourceRefs");
        assertEquals(1, sourceRefs.size());
        assertEquals("requirement", sourceRefs.get(0).get("type"));
        assertEquals(REQUIREMENT_ID.toString(), sourceRefs.get(0).get("id"));
    }

    @Test
    void proposalParser_noValidCase_failsModel() {
        Requirement confirmed = requirement(PROJECT_ID);
        confirmed.setTitle("短信验证码登录");
        when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(confirmed);
        when(testCaseDocumentMapper.selectById(DOCUMENT_ID)).thenReturn(document());
        when(testCaseNodeMapper.listCasesByDocumentIds(anyList())).thenReturn(List.of());
        when(vectorSearchService.search(any(), anyList(), any(), anyInt(), any())).thenReturn(List.of());
        AgentExecutionPreparation preparation = prepare(input());

        ServiceException error = assertThrows(ServiceException.class,
                () -> preparation.proposalParser().apply("{\"artifacts\":[{\"title\":\"无来源\",\"content\":{}}]}"));
        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), error.getCode());
    }

    private AgentExecutionPreparation prepare(Map<String, Object> input) {
        return adapter.prepare(new TaskTypeAdapter.PreparationContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), PROJECT_ID, input, null));
    }

    private static Map<String, Object> input() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("documentId", DOCUMENT_ID.toString());
        input.put("requirementId", REQUIREMENT_ID.toString());
        return input;
    }

    private static TaskSubmitContext context() {
        return new TaskSubmitContext(PROJECT_ID, UUID.randomUUID(), UUID.randomUUID(), null);
    }

    private static Requirement requirement(UUID projectId) {
        return requirement(projectId, Constants.RequirementStatus.CONFIRMED);
    }

    private static Requirement requirement(UUID projectId, String status) {
        Requirement requirement = new Requirement();
        requirement.setId(REQUIREMENT_ID);
        requirement.setProjectId(projectId);
        requirement.setStatus(status);
        return requirement;
    }

    private static TestCaseDocument document() {
        TestCaseDocument document = new TestCaseDocument();
        document.setId(DOCUMENT_ID);
        document.setProjectId(PROJECT_ID);
        return document;
    }
}
