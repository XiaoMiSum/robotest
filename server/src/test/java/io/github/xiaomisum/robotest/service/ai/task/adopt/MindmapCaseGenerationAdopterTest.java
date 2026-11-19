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

/** 脑图生成用例确认承接单测（C8）：节点插入由脑图前端执行，后端承接器只回空结果。 */
class MindmapCaseGenerationAdopterTest {

    private final MindmapCaseGenerationAdopter adopter = new MindmapCaseGenerationAdopter();

    @Test
    void type_isMindmapCaseGeneration() {
        assertEquals(Constants.AiTaskType.MINDMAP_CASE_GENERATION, adopter.type());
    }

    @Test
    void adopt_allActions_returnNullWithoutNodeChanges() {
        for (String action : List.of(Constants.AiProposalAction.ADOPTED,
                Constants.AiProposalAction.ADOPTED_EDITED, Constants.AiProposalAction.REJECTED)) {
            assertNull(adopter.adopt(context(action)));
        }
    }

    private AdoptContext context(String action) {
        LoginUser loginUser = mock(LoginUser.class);
        AiTask task = new AiTask();
        task.setType(Constants.AiTaskType.MINDMAP_CASE_GENERATION);
        Map<String, Object> artifact = Map.of("key", "case-1", "content",
                Map.of("title", "获取验证码", "priority", "P0"));
        return new AdoptContext(task, AdoptFixtures.proposal(artifact), action, null, null,
                null, null, null, null, null, null,
                UUID.randomUUID(), UUID.randomUUID(), Set.of(), loginUser);
    }
}
