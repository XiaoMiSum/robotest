package io.github.xiaomisum.robotest.service.ai.task.handler;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskResult;
import io.github.xiaomisum.robotest.service.ai.task.TaskSubmitContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 圈选建议处理器单测（C8）：轮次入参规则、范围与按范围权限、候选用例装配与产物清洗。
 */
@ExtendWith(MockitoExtension.class)
class SelectionHandlerTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID OPERATOR_ID = UUID.randomUUID();
    private static final UUID REQUIREMENT_ID = UUID.randomUUID();
    private static final UUID MODULE_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final UUID CASE_ID = UUID.randomUUID();

    @Mock
    private RequirementMapper requirementMapper;
    @Mock
    private ProjectModuleMapper projectModuleMapper;
    @Mock
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private TaskExecutionContext context;

    @InjectMocks
    private ReviewSelectionHandler reviewHandler;

    @InjectMocks
    private PlanSelectionHandler planHandler;

    // ---------- 轮次入参规则 ----------

    @Test
    void review_withRoundCount_throws206() {
        Map<String, Object> input = input();
        input.put("roundCount", 2);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> reviewHandler.validateInput(input));
        assertEquals(ErrorCodeConstants.SELECTION_INPUT_INVALID.code(), exception.getCode());
    }

    @Test
    void plan_withoutRoundCount_throws206() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> planHandler.validateInput(input()));
        assertEquals(ErrorCodeConstants.SELECTION_INPUT_INVALID.code(), exception.getCode());
    }

    @Test
    void plan_roundCountOutOfDomain_throws206() {
        Map<String, Object> input = input();
        input.put("roundCount", 11);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> planHandler.validateInput(input));
        assertEquals(ErrorCodeConstants.SELECTION_INPUT_INVALID.code(), exception.getCode());
    }

    @Test
    void plan_validRoundCount_passes() {
        Map<String, Object> input = input();
        input.put("roundCount", 3);

        planHandler.validateInput(input);
    }

    // ---------- 范围与权限 ----------

    @Test
    void validateInput_emptyScope_throws206() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> reviewHandler.validateInput(Map.of()));
        assertEquals(ErrorCodeConstants.SELECTION_INPUT_INVALID.code(), exception.getCode());
    }

    @Test
    void validateInput_withoutProjectContext_throws2008() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> reviewHandler.validateInput(input(),
                        new TaskSubmitContext(null, null, OPERATOR_ID, null)));
        assertEquals(ErrorCodeConstants.CONTEXT_HEADER_MISSING.code(), exception.getCode());
    }

    @Test
    void validateInput_requirementNotInProject_throws206() {
        when(requirementMapper.listByIds(any())).thenReturn(List.of());
        Map<String, Object> input = input();

        ServiceException exception = assertThrows(ServiceException.class,
                () -> reviewHandler.validateInput(input, submitContext()));
        assertEquals(ErrorCodeConstants.SELECTION_INPUT_INVALID.code(), exception.getCode());
    }

    @Test
    void validateInput_requirementScopeWithAuthority_passes() {
        when(requirementMapper.listByIds(any())).thenReturn(List.of(requirement()));
        LoginUser user = user(List.of("requirement:view"));
        TaskSubmitContext ctx = new TaskSubmitContext(PROJECT_ID, UUID.randomUUID(), OPERATOR_ID, user);

        reviewHandler.validateInput(input(), ctx);
    }

    // ---------- 执行：装配与清洗 ----------

    @Test
    @SuppressWarnings("unchecked")
    void execute_filtersOutOfScopeAndNormalizesRound() {
        stubExecutionBase();
        when(requirementMapper.listByIds(any())).thenReturn(List.of(requirement()));
        when(projectModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(module()));
        when(testCaseDocumentMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(document()));
        when(testCaseNodeMapper.listCasesByDocumentIds(any())).thenReturn(List.of(caseNode()));
        when(testCaseDocumentMapper.listByIds(any())).thenReturn(List.of(document()));
        when(context.chat(anyString(), anyString())).thenReturn(selectionReply());

        Map<String, Object> submit = input();
        submit.put("roundCount", 2);
        when(context.getInput()).thenReturn(submit);

        TaskResult result = planHandler.execute(context);

        List<Map<String, Object>> artifacts =
                (List<Map<String, Object>>) result.result().get("artifacts");
        assertEquals(1, artifacts.size());
        Map<String, Object> artifact = artifacts.get(0);
        assertEquals("sel-1", artifact.get("key"));
        assertEquals("plan_selection", artifact.get("kind"));
        assertEquals("计划圈选建议（1 条）", artifact.get("title"));

        List<Map<String, Object>> items =
                (List<Map<String, Object>>) ((Map<String, Object>) artifact.get("content")).get("items");
        assertEquals(1, items.size());
        assertEquals(CASE_ID.toString(), items.get(0).get("caseId"));
        // round=9 超出 roundCount=2 → 收敛到 2
        assertEquals(2, items.get(0).get("round"));
    }

    @Test
    void execute_emptyCandidates_throws206() {
        stubExecutionBase();
        when(requirementMapper.listByIds(any())).thenReturn(List.of(requirement()));
        when(projectModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(module()));
        when(testCaseDocumentMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of());

        ServiceException exception = assertThrows(ServiceException.class,
                () -> reviewHandler.execute(context));
        assertEquals(ErrorCodeConstants.SELECTION_INPUT_INVALID.code(), exception.getCode());
    }

    @Test
    void type_isSelectionKind() {
        assertEquals("review_selection", reviewHandler.type());
        assertEquals("plan_selection", planHandler.type());
        assertTrue(reviewHandler.defaultPrompt().contains("评审"));
        assertTrue(planHandler.defaultPrompt().contains("roundCount"));
    }

    // ---------- 辅助 ----------

    private void stubExecutionBase() {
        when(context.getInput()).thenReturn(input());
        when(context.getProjectId()).thenReturn(PROJECT_ID);
        // 产出提示词经 context.prompt 解析（场景行 / 内置回落），返回非空供 chat 承接
        lenient().when(context.prompt(anyString(), any())).thenReturn("scope-context");
    }

    private Map<String, Object> input() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("requirementIds", List.of(REQUIREMENT_ID.toString()));
        return input;
    }

    private TaskSubmitContext submitContext() {
        return new TaskSubmitContext(PROJECT_ID, UUID.randomUUID(), OPERATOR_ID, null);
    }

    private static LoginUser user(List<String> permissions) {
        LoginUser user = org.mockito.Mockito.mock(LoginUser.class);
        // 权限不再由输入校验消费，登录态仅作上下文传递（桩不再被消费，置 lenient）
        lenient().when(user.getPermissions()).thenReturn(permissions);
        return user;
    }

    private Requirement requirement() {
        Requirement requirement = new Requirement();
        requirement.setId(REQUIREMENT_ID);
        requirement.setProjectId(PROJECT_ID);
        requirement.setModuleId(MODULE_ID);
        requirement.setCode("REQ-1");
        requirement.setTitle("用户登录");
        return requirement;
    }

    private ProjectModule module() {
        ProjectModule module = new ProjectModule();
        module.setId(MODULE_ID);
        module.setProjectId(PROJECT_ID);
        module.setName("登录");
        return module;
    }

    private TestCaseNode caseNode() {
        TestCaseNode node = new TestCaseNode();
        node.setId(CASE_ID);
        node.setDocumentId(DOCUMENT_ID);
        node.setType("case");
        node.setTitle("正确密码登录");
        node.setPriority("P1");
        return node;
    }

    private TestCaseDocument document() {
        TestCaseDocument document = new TestCaseDocument();
        document.setId(DOCUMENT_ID);
        document.setProjectId(PROJECT_ID);
        document.setModuleId(MODULE_ID);
        document.setName("登录用例");
        return document;
    }

    private AiChatReply selectionReply() {
        String json = "{\"artifacts\":[{\"key\":\"sel-1\",\"content\":{\"items\":["
                + "{\"caseId\":\"" + CASE_ID + "\",\"title\":\"正确密码登录\",\"reason\":\"核心路径\",\"round\":9},"
                + "{\"caseId\":\"" + UUID.randomUUID() + "\",\"title\":\"越界\",\"reason\":\"越界\",\"round\":1}"
                + "]}}]}";
        return new AiChatReply(json, 12, 6);
    }
}
