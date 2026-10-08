package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiVectorSearchHitRespDTO;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.vector.VectorSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BugDuplicateScanHandlerTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID CANONICAL_ID = UUID.randomUUID();
    private static final UUID DUPLICATE_ID = UUID.randomUUID();
    private static final UUID OUTSIDE_ID = UUID.randomUUID();

    @Mock
    private BugMapper bugMapper;
    @Mock
    private VectorSearchService vectorSearchService;
    @Mock
    private TaskExecutionContext context;

    @InjectMocks
    private BugDuplicateScanHandler handler;

    private Bug active(String title, LocalDateTime createdAt) {
        Bug bug = new Bug();
        bug.setId(UUID.randomUUID());
        bug.setProjectId(PROJECT_ID);
        bug.setTitle(title);
        bug.setStatus("active");
        bug.setCreatedAt(createdAt);
        return bug;
    }

    private void stubContext(AiChatReply reply) {
        lenient().when(context.getProjectId()).thenReturn(PROJECT_ID);
        lenient().when(context.getUserId()).thenReturn(UUID.randomUUID());
        lenient().when(context.prompt(any(), any())).thenReturn("prompt");
        lenient().when(context.chat(any(), any())).thenReturn(reply);
    }

    private Bug canonical() {
        Bug bug = active("登录按钮无反应", LocalDateTime.parse("2026-09-01T10:00:00"));
        bug.setId(CANONICAL_ID);
        return bug;
    }

    private Bug duplicate() {
        Bug bug = active("点击登录按钮后页面无响应", LocalDateTime.parse("2026-09-02T10:00:00"));
        bug.setId(DUPLICATE_ID);
        return bug;
    }

    private void stubScopeAndHits(List<Bug> bugs) {
        when(bugMapper.listByStatus(eq(PROJECT_ID), any(), anyInt())).thenReturn(bugs);
        AiVectorSearchHitRespDTO hit = new AiVectorSearchHitRespDTO();
        hit.setEntityId(DUPLICATE_ID);
        hit.setEntityType("bug");
        hit.setDistance(0.17);
        when(vectorSearchService.search(any(), anyList(), eq("bug"), anyInt(), any()))
                .thenReturn(List.of(hit));
    }

    // ---------- SPI 契约 ----------

    @Test
    void type_isBugDuplicateScan() {
        assertEquals("bug_duplicate_scan", handler.type());
        assertTrue(handler.defaultPrompt().contains("{{bugContext}}"));
    }

    @Test
    void validateInput_unsupportedScope_throws() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.validateInput(Map.of("scope", "closed")));
        assertEquals(ErrorCodeConstants.BUG_ANALYSIS_SCOPE_INVALID.code(), exception.getCode());
    }

    @Test
    void validateInput_defaultScopeActive_passes() {
        handler.validateInput(Map.of());
        handler.validateInput(Map.of("scope", "active"));
    }

    @Test
    void checkPermission_withoutBugView_throwsNoPermission() {
        LoginUser user = mock(LoginUser.class);
        when(user.getPermissions()).thenReturn(List.of("ai:task"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.checkPermission(user));
        assertEquals(ErrorCodeConstants.NO_PERMISSION.code(), exception.getCode());
    }

    // ---------- 执行 ----------

    @Test
    void execute_emptyScope_throwsInputEmpty() {
        lenient().when(context.getProjectId()).thenReturn(PROJECT_ID);
        when(bugMapper.listByStatus(eq(PROJECT_ID), any(), anyInt())).thenReturn(List.of());

        ServiceException exception = assertThrows(ServiceException.class, () -> handler.execute(context));
        assertEquals(ErrorCodeConstants.BUG_ANALYSIS_INPUT_EMPTY.code(), exception.getCode());
    }

    @Test
    void execute_sanitizesGroups() {
        stubScopeAndHits(List.of(canonical(), duplicate()));
        stubContext(new AiChatReply("""
                {"groups":[
                  {"canonicalBugId":"%s","items":[
                    {"bugId":"%s","similarity":0.83,"reason":"同现象同根因"},
                    {"bugId":"%s","reason":"范围外应剔除"}]}]}
                """.formatted(CANONICAL_ID, DUPLICATE_ID, OUTSIDE_ID), 10, 5));

        var result = handler.execute(context);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> artifacts = (List<Map<String, Object>>) result.result().get("artifacts");
        assertEquals(1, artifacts.size());
        Map<String, Object> artifact = artifacts.get(0);
        assertEquals("group-1", artifact.get("key"));
        assertEquals("duplicate_group", artifact.get("kind"));
        assertEquals("疑似重复分组（2 条）", artifact.get("title"));
        assertEquals("pending", artifact.get("confirmStatus"));

        @SuppressWarnings("unchecked")
        Map<String, Object> content = (Map<String, Object>) artifact.get("content");
        assertEquals(CANONICAL_ID.toString(), content.get("canonicalBugId"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) content.get("items");
        assertEquals(1, items.size());
        assertEquals(DUPLICATE_ID.toString(), items.get(0).get("bugId"));
        assertEquals(0.83, items.get(0).get("similarity"));
    }

    @Test
    void execute_groupWithoutValidItems_dropped() {
        stubScopeAndHits(List.of(canonical(), duplicate()));
        stubContext(new AiChatReply("""
                {"groups":[{"canonicalBugId":"%s","items":[]}]}
                """.formatted(CANONICAL_ID), 10, 5));

        var result = handler.execute(context);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> artifacts = (List<Map<String, Object>>) result.result().get("artifacts");
        assertTrue(artifacts.isEmpty());
    }
}
