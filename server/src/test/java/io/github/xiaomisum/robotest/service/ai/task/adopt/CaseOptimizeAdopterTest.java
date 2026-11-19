package io.github.xiaomisum.robotest.service.ai.task.adopt;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.AdoptFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

/**
 * 用例优化确认承接单测（C8）：节点变更在脑图前端执行并经 WS 落库，
 * 后端承接器只回空结果，采纳状态由确认入口统一记录（详设 3.2 / 4.2）。
 */
class CaseOptimizeAdopterTest {

    private final CaseOptimizeAdopter adopter = new CaseOptimizeAdopter();

    @Test
    void type_isCaseOptimize() {
        assertEquals("case_optimize", adopter.type());
    }

    @Test
    void adopt_allActions_returnNullWithoutNodeChanges() {
        for (String action : List.of(Constants.AiProposalAction.ADOPTED,
                Constants.AiProposalAction.ADOPTED_EDITED,
                Constants.AiProposalAction.REJECTED)) {
            assertNull(adopter.adopt(context(action)));
        }
    }

    private AdoptContext context(String action) {
        LoginUser loginUser = mock(LoginUser.class);
        AiTask task = new AiTask();
        task.setType(Constants.AiTaskType.CASE_OPTIMIZE);
        Map<String, Object> artifact = Map.of("key", "node-1", "content",
                Map.of("nodeId", UUID.randomUUID().toString(), "operations", List.of()));
        return new AdoptContext(task, AdoptFixtures.proposal(artifact), action, null, null,
                null, null, null, null, null, null,
                UUID.randomUUID(), UUID.randomUUID(), Set.of(), loginUser);
    }
}
