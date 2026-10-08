package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BugTriageHandlerTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID BUG_ID = UUID.randomUUID();
    private static final UUID OUTSIDE_ID = UUID.randomUUID();

    @Mock
    private BugMapper bugMapper;
    @Mock
    private TaskExecutionContext context;

    @InjectMocks
    private BugTriageHandler handler;

    private Bug activeUnassigned() {
        Bug bug = new Bug();
        bug.setId(BUG_ID);
        bug.setProjectId(PROJECT_ID);
        bug.setTitle("登录按钮无反应");
        bug.setSeverity("serious");
        bug.setPriority("high");
        bug.setStatus("active");
        bug.setCreatedAt(LocalDateTime.now().minusDays(6));
        return bug;
    }

    private void stubContext(AiChatReply reply) {
        lenient().when(context.getProjectId()).thenReturn(PROJECT_ID);
        lenient().when(context.prompt(any(), any())).thenReturn("prompt");
        lenient().when(context.chat(any(), any())).thenReturn(reply);
    }

    // ---------- SPI 契约 ----------

    @Test
    void type_isBugTriage() {
        assertEquals("bug_triage", handler.type());
        assertTrue(handler.defaultPrompt().contains("{{bugContext}}"));
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
        when(bugMapper.listActiveUnassigned(PROJECT_ID)).thenReturn(List.of());

        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.execute(context));
        assertEquals(ErrorCodeConstants.BUG_ANALYSIS_INPUT_EMPTY.code(), exception.getCode());
    }

    @Test
    void execute_dropsOutOfScopeAndRenumbers() {
        when(bugMapper.listActiveUnassigned(PROJECT_ID)).thenReturn(List.of(activeUnassigned()));
        stubContext(new AiChatReply("""
                {"items":[
                  {"bugId":"%s","rank":1,"reason":"高严重等级且滞留 6 天"},
                  {"bugId":"%s","rank":2,"reason":"范围外应剔除"},
                  {"bugId":"%s","rank":3,"reason":"重复应剔除"}]}
                """.formatted(OUTSIDE_ID, BUG_ID, BUG_ID), 8, 4));

        var result = handler.execute(context);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> artifacts = (List<Map<String, Object>>) result.result().get("artifacts");
        assertEquals(1, artifacts.size());
        Map<String, Object> artifact = artifacts.get(0);
        assertEquals("triage", artifact.get("key"));
        assertEquals("triage_order", artifact.get("kind"));
        assertEquals("not_applicable", artifact.get("confirmStatus"));

        @SuppressWarnings("unchecked")
        Map<String, Object> content = (Map<String, Object>) artifact.get("content");
        assertEquals("active_unassigned", content.get("scope"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) content.get("items");
        // 范围外与重复项被剔除，rank 重排为连续序号
        assertEquals(1, items.size());
        assertEquals(BUG_ID.toString(), items.get(0).get("bugId"));
        assertEquals(1, items.get(0).get("rank"));
    }

    @Test
    void execute_tokensPassedThrough() {
        when(bugMapper.listActiveUnassigned(PROJECT_ID)).thenReturn(List.of(activeUnassigned()));
        stubContext(new AiChatReply("{\"items\":[]}", 8, 4));

        var result = handler.execute(context);

        assertEquals(8, result.tokensIn());
        assertEquals(4, result.tokensOut());
    }
}
