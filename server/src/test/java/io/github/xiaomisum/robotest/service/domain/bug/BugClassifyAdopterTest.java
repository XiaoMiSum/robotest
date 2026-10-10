package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugUpdateReqDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.AdoptOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BugClassifyAdopterTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID OPERATOR_ID = UUID.randomUUID();
    private static final UUID BUG_ID = UUID.randomUUID();
    private static final UUID MODULE_ID = UUID.randomUUID();
    private static final UUID ASSIGNEE_ID = UUID.randomUUID();

    @Mock
    private BugService bugService;

    @InjectMocks
    private BugClassifyAdopter adopter;

    private AdoptContext context(String action, Map<String, Object> content, List<String> permissions) {
        LoginUser loginUser = mock(LoginUser.class);
        org.mockito.Mockito.lenient().when(loginUser.getPermissions()).thenReturn(permissions);
        Map<String, Object> artifact = Map.of("key", "bug-" + BUG_ID, "content", suggestionsContent());
        AiTask task = new AiTask();
        task.setType(BugClassifyHandler.TYPE);
        return new AdoptContext(task, artifact, action, content, null, null, null, null, null,
                null, null, null, null, PROJECT_ID, OPERATOR_ID, loginUser);
    }

    private Map<String, Object> suggestionsContent() {
        return Map.of(
                "bugId", BUG_ID.toString(),
                "suggestions", Map.of(
                        "bugType", Map.of("value", "code_error"),
                        "severity", Map.of("value", "serious"),
                        "priority", Map.of("value", "high"),
                        "moduleId", Map.of("value", MODULE_ID.toString()),
                        "keywords", Map.of("value", List.of("登录", "无响应")),
                        "assigneeId", Map.of("value", ASSIGNEE_ID.toString())));
    }

    @Test
    void type_isBugClassify() {
        assertEquals("bug_classify", adopter.type());
    }

    @Test
    void adopt_rejected_returnsNullAndSkipsUpdate() {
        AdoptOutcome outcome = adopter.adopt(context(Constants.AiArtifactAction.REJECTED, null,
                List.of("bug:edit")));

        assertNull(outcome);
        verify(bugService, never()).updateBug(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void adopt_withoutEditPermission_throwsNoPermission() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> adopter.adopt(context(Constants.AiArtifactAction.ADOPTED, suggestionsContent(),
                        List.of("bug:view"))));
        assertEquals(ErrorCodeConstants.NO_PERMISSION.code(), exception.getCode());
    }

    @Test
    void adopt_appliesSuggestionsViaPartialUpdate() {
        AdoptOutcome outcome = adopter.adopt(context(Constants.AiArtifactAction.ADOPTED, null,
                List.of("bug:edit")));

        ArgumentCaptor<BugUpdateReqDTO> captor = ArgumentCaptor.forClass(BugUpdateReqDTO.class);
        verify(bugService).updateBug(org.mockito.ArgumentMatchers.eq(PROJECT_ID),
                org.mockito.ArgumentMatchers.eq(BUG_ID), org.mockito.ArgumentMatchers.eq(OPERATOR_ID),
                captor.capture());
        BugUpdateReqDTO update = captor.getValue();
        assertEquals("code_error", update.getBugType());
        assertEquals("serious", update.getSeverity());
        assertEquals("high", update.getPriority());
        assertEquals(MODULE_ID, update.getModuleId());
        assertEquals("登录,无响应", update.getKeywords());
        assertEquals(ASSIGNEE_ID, update.getAssigneeId());
        // 标题等未建议字段不进更新载体（C11 部分更新）
        assertNull(update.getTitle());

        assertEquals(BUG_ID, outcome.createdId());
        assertEquals(BUG_ID.toString(), outcome.adoptedRef().get("bugId"));
    }

    @Test
    void adopt_editedContentOverridesArtifact() {
        Map<String, Object> edited = Map.of(
                "bugId", BUG_ID.toString(),
                "suggestions", Map.of("severity", Map.of("value", "minor")));
        adopter.adopt(context(Constants.AiArtifactAction.ADOPTED_EDITED, edited, List.of("bug:edit")));

        ArgumentCaptor<BugUpdateReqDTO> captor = ArgumentCaptor.forClass(BugUpdateReqDTO.class);
        verify(bugService).updateBug(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), captor.capture());
        assertEquals("minor", captor.getValue().getSeverity());
        assertNull(captor.getValue().getBugType());
        assertTrue(captor.getValue().getKeywords() == null);
    }
}
