package io.github.xiaomisum.robotest.service.ai.task.adopt;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.AdoptFixtures;
import io.github.xiaomisum.robotest.service.project.ProjectActivityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AssistAdoptSupportTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID OPERATOR_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final UUID NODE_ID = UUID.randomUUID();

    @Mock
    private ProjectActivityService projectActivityService;

    @InjectMocks
    private AssistAdoptSupport support;

    @Test
    void taskInput_absentInput_returnsEmptyMap() {
        Map<String, Object> nodeContent = Map.of("nodeId", NODE_ID.toString());

        assertTrue(AssistAdoptSupport.taskInput(context(nodeContent, null)).isEmpty());
    }

    @Test
    void record_delegatesToActivityService() {
        Map<String, Object> nodeContent = Map.of("nodeId", NODE_ID.toString());
        AdoptContext context = context(nodeContent, Map.of("documentId", DOCUMENT_ID.toString()));

        support.record(context, "TEST_CASE_NODE", NODE_ID, "用例标题", "CASE_UPDATED", "采纳 AI 用例优化建议");

        verify(projectActivityService).record(eq(PROJECT_ID), eq(OPERATOR_ID), eq("TEST_CASE_NODE"),
                eq(NODE_ID), eq("用例标题"), eq("CASE_UPDATED"), eq("采纳 AI 用例优化建议"));
    }

    // ---------- fixtures ----------

    private AdoptContext context(Map<String, Object> nodeContent, Map<String, Object> input) {
        LoginUser loginUser = mock(LoginUser.class);
        AiTask task = new AiTask();
        task.setType("case_optimize");
        task.setInput(input);
        Map<String, Object> artifact = Map.of("key", "node-1", "content", nodeContent);
        return new AdoptContext(task, AdoptFixtures.proposal(artifact), "adopted", null, null, null, null, null,
                null, null, null, PROJECT_ID, OPERATOR_ID, Set.of(), loginUser);
    }
}
