package io.github.xiaomisum.robotest.service.ai.task.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopedSnapshotAgentToolsTest {

    @Test
    void execute_rejectsResourceOutsideInputSnapshot() {
        UUID trusted = UUID.randomUUID();
        UUID outside = UUID.randomUUID();
        AgentToolContext context = new AgentToolContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "bug_classify", Map.of("bugIds", List.of(trusted.toString())));

        String result = new ScopedSnapshotAgentTools.QueryBugs().execute(context,
                Map.of("resourceIds", List.of(outside.toString())));

        assertTrue(result.contains("超出任务可信范围"));
    }
}
