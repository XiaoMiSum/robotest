package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.AdoptOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BugDuplicateScanAdopterTest {

    private final BugDuplicateScanAdopter adopter = new BugDuplicateScanAdopter();

    @Test
    void type_isBugDuplicateScan() {
        assertEquals("bug_duplicate_scan", adopter.type());
    }

    @Test
    void adopt_recordsConclusionWithoutTouchingBugs() {
        UUID canonicalId = UUID.randomUUID();
        AiTask task = new AiTask();
        task.setType("bug_duplicate_scan");
        Map<String, Object> artifact = Map.of("key", "group-1",
                "content", Map.of("canonicalBugId", canonicalId.toString(),
                        "items", List.of(Map.of("bugId", UUID.randomUUID().toString()))));
        AdoptContext context = new AdoptContext(task, artifact, Constants.AiArtifactAction.ADOPTED,
                null, "确认为重复", null, null, null, null, null, null, null, null,
                UUID.randomUUID(), UUID.randomUUID(), (LoginUser) null);

        AdoptOutcome outcome = adopter.adopt(context);

        // 留痕型承接：只回执结论引用，不产生业务实体
        assertNull(outcome.createdId());
        assertEquals(canonicalId.toString(), outcome.adoptedRef().get("canonicalBugId"));
        assertEquals(1, outcome.adoptedRef().get("itemCount"));
    }
}
