package io.github.xiaomisum.robotest.service.ai.assistant.tool;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantPromptSupport;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantRefChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ErrorCode;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 意图规范化单测（详设 4.1 / 4.2 / 4.3）：kind 与作用域派生、引用越权拒绝、
 * 字段白名单清洗、createCount 派生与过期固化（C8）。
 */
@ExtendWith(MockitoExtension.class)
class AssistantIntentBuilderTest {

    private static final UUID WS = UUID.randomUUID();
    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID CASE_ID = UUID.randomUUID();
    private static final UUID TARGET_ID = UUID.randomUUID();
    private static final int PARSE_FAILED = ErrorCodeConstants.ASSISTANT_PARSE_FAILED.code();
    private static final ErrorCode REF_DENIED = ErrorCode.of(1000018261, "引用越权");

    @Mock
    private AssistantRefChecker refChecker;
    @Mock
    private AssistantPromptSupport promptSupport;

    @InjectMocks
    private AssistantIntentBuilder builder;

    @Test
    void create_case_freezes_scope_and_derives_count_from_cases() {
        when(refChecker.requireActiveWorkspaceCases(eq(WS), any())).thenReturn(Set.of());
        when(promptSupport.projectName(PROJECT)).thenReturn("电商商城");
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("targetTitle", "登录用例");
        raw.put("projectId", PROJECT.toString());
        raw.put("params", Map.of("cases", List.of(Map.of("title", "a"), Map.of("title", "b"))));

        Map<String, Object> intent = builder.normalize("create_case", raw, WS);

        assertEquals("create_case", intent.get("kind"));
        assertEquals(Constants.TraceNodeType.TEST_CASE, intent.get("targetType"));
        assertEquals(PROJECT, intent.get("projectId"));
        assertEquals(2, intent.get("createCount"));
        assertEquals(0, ((List<?>) intent.get("changes")).size());
        Map<?, ?> scope = (Map<?, ?>) intent.get("scope");
        assertEquals(WS, scope.get("workspaceId"));
        assertEquals("电商商城", scope.get("projectName"));
        // 过期时间 = 现在起 10 分钟内（详设 4.3）
        LocalDateTime expiresAt = LocalDateTime.parse(String.valueOf(intent.get("expiresAt")));
        assertTrue(expiresAt.isAfter(LocalDateTime.now()));
        assertTrue(expiresAt.isBefore(LocalDateTime.now().plusMinutes(11)));
        verify(refChecker).requireActiveWorkspaceProject(WS, PROJECT);
    }

    @Test
    void create_count_defaults_and_clamps() {
        when(refChecker.requireActiveWorkspaceCases(eq(WS), any())).thenReturn(Set.of());
        when(promptSupport.projectName(PROJECT)).thenReturn("P");

        Map<String, Object> withCount = new LinkedHashMap<>();
        withCount.put("targetTitle", "计划A");
        withCount.put("projectId", PROJECT.toString());
        withCount.put("createCount", 999_999);
        assertEquals(10_000, builder.normalize("create_plan", withCount, WS).get("createCount"));

        Map<String, Object> plainCreate = new LinkedHashMap<>();
        plainCreate.put("targetTitle", "计划B");
        plainCreate.put("projectId", PROJECT.toString());
        assertEquals(1, builder.normalize("create_plan", plainCreate, WS).get("createCount"));
    }

    @Test
    void non_create_kind_has_zero_count() {
        when(refChecker.requireActiveWorkspaceCases(eq(WS), eq(Set.of(CASE_ID))))
                .thenReturn(Set.of(PROJECT));
        when(promptSupport.projectName(PROJECT)).thenReturn("P");
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("targetTitle", "登录用例");
        raw.put("projectId", PROJECT.toString());
        raw.put("params", Map.of("caseIds", List.of(CASE_ID.toString())));

        Map<String, Object> intent = builder.normalize("update_case", raw, WS);

        assertEquals(0, intent.get("createCount"));
        // caseIds 已回填为 UUID 集（详设 4.1 规范化）
        assertEquals(List.of(CASE_ID), ((Map<?, ?>) intent.get("params")).get("caseIds"));
    }

    @Test
    void foreign_case_reference_propagates_ref_error() {
        when(refChecker.requireActiveWorkspaceCases(eq(WS), eq(Set.of(CASE_ID))))
                .thenThrow(ServiceExceptionUtil.get(REF_DENIED));
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("targetTitle", "受影响用例");
        raw.put("params", Map.of("caseIds", List.of(CASE_ID.toString())));

        ServiceException error = assertThrows(ServiceException.class,
                () -> builder.normalize("update_case", raw, WS));
        assertEquals(1000018261, error.getCode());
    }

    @Test
    void multi_project_case_scope_rejected() {
        when(refChecker.requireActiveWorkspaceCases(eq(WS), any()))
                .thenReturn(Set.of(UUID.randomUUID(), UUID.randomUUID()));
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("targetTitle", "x");
        raw.put("params", Map.of("caseIds", List.of(CASE_ID.toString())));
        ServiceException error = assertThrows(ServiceException.class,
                () -> builder.normalize("update_case", raw, WS));
        assertEquals(PARSE_FAILED, error.getCode());
    }

    @Test
    void adjust_review_derives_scope_and_title_from_target() {
        when(refChecker.requireActiveWorkspaceCases(eq(WS), any())).thenReturn(Set.of());
        when(refChecker.projectIdOf(Constants.TraceNodeType.TEST_REVIEW, TARGET_ID))
                .thenReturn(PROJECT);
        when(refChecker.titleOf(Constants.TraceNodeType.TEST_REVIEW, TARGET_ID))
                .thenReturn("登录评审");
        when(promptSupport.projectName(PROJECT)).thenReturn("P");
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("targetId", TARGET_ID.toString());

        Map<String, Object> intent = builder.normalize("adjust_review", raw, WS);

        assertEquals(Constants.TraceNodeType.TEST_REVIEW, intent.get("targetType"));
        assertEquals(TARGET_ID, intent.get("targetId"));
        assertEquals("登录评审", intent.get("targetTitle"));
        assertEquals(PROJECT, intent.get("projectId"));
        verify(refChecker).requireActiveWorkspaceRef(WS, Constants.TraceNodeType.TEST_REVIEW, TARGET_ID);
    }

    @Test
    void changes_are_sanitized_and_field_less_dropped() {
        when(refChecker.requireActiveWorkspaceCases(eq(WS), any())).thenReturn(Set.of());
        when(promptSupport.projectName(PROJECT)).thenReturn("P");
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("targetTitle", "登录用例");
        raw.put("projectId", PROJECT.toString());
        raw.put("changes", List.of(
                Map.of("field", "title", "op", "replace", "value", "新标题"),
                Map.of("op", "add", "value", "丢弃：无 field"),
                Map.of("field", "priority", "op", "怪值", "value", "P1"),
                Map.of("field", "steps", "op", "add", "value", "x".repeat(1500))));

        Map<String, Object> intent = builder.normalize("create_case", raw, WS);

        List<?> changes = (List<?>) intent.get("changes");
        assertEquals(3, changes.size());
        Map<?, ?> first = (Map<?, ?>) changes.get(0);
        assertEquals("replace", first.get("op"));
        assertEquals("新标题", first.get("value"));
        // 非法 op 收敛为 add（字段级越界由执行侧白名单忽略）
        assertEquals("add", ((Map<?, ?>) changes.get(1)).get("op"));
        assertTrue(String.valueOf(((Map<?, ?>) changes.get(2)).get("value")).length() <= 1001);
    }

    @Test
    void missing_scope_rejected_as_parse_failed() {
        when(refChecker.requireActiveWorkspaceCases(eq(WS), any())).thenReturn(Set.of());
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("targetTitle", "登录用例");
        ServiceException error = assertThrows(ServiceException.class,
                () -> builder.normalize("create_case", raw, WS));
        assertEquals(PARSE_FAILED, error.getCode());
        assertNull(raw.get("projectId"));
    }

    @Test
    void illegal_kind_target_and_workspace_rejected() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("targetTitle", "x");
        assertEquals(PARSE_FAILED, assertThrows(ServiceException.class,
                () -> builder.normalize("view_plan_progress", raw, WS)).getCode());
        assertEquals(PARSE_FAILED, assertThrows(ServiceException.class,
                () -> builder.normalize("create_case", raw, null)).getCode());
        raw.put("targetId", "not-a-uuid");
        assertEquals(PARSE_FAILED, assertThrows(ServiceException.class,
                () -> builder.normalize("adjust_plan", raw, WS)).getCode());
    }
}
