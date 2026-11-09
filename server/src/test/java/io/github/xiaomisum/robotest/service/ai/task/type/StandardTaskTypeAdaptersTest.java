package io.github.xiaomisum.robotest.service.ai.task.type;

import io.github.xiaomisum.robotest.service.domain.bug.BugClassifyTypeAdapter;
import io.github.xiaomisum.robotest.service.domain.bug.BugDuplicateScanTypeAdapter;
import io.github.xiaomisum.robotest.service.domain.bug.BugTriageTypeAdapter;
import io.github.xiaomisum.robotest.service.domain.bug.BugTrendSummaryTypeAdapter;
import io.github.xiaomisum.robotest.service.domain.requirement.RequirementImportTypeAdapter;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantParseTypeAdapter;
import io.github.xiaomisum.robotest.service.ai.vector.VectorReindexTypeAdapter;
import org.junit.jupiter.api.Test;

import io.github.xiaomisum.robotest.service.ai.task.core.AgentToolRegistry;
import io.github.xiaomisum.robotest.service.ai.task.core.ScopedSnapshotAgentTools;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardTaskTypeAdaptersTest {

    @Test
    void elevenOrdinaryAdaptersUseUnifiedBudgetAndCoverSeededTools() {
        List<TaskTypeAdapter> adapters = List.of(
                new RequirementImportTypeAdapter(), new TestDesignGenerationTypeAdapter(),
                new ReviewSelectionTypeAdapter(), new PlanSelectionTypeAdapter(),
                new CaseCompleteTypeAdapter(), new CasePriorityTypeAdapter(), new PlanOrderTypeAdapter(),
                new BugClassifyTypeAdapter(), new BugDuplicateScanTypeAdapter(),
                new BugTriageTypeAdapter(), new BugTrendSummaryTypeAdapter());

        assertEquals(11, adapters.stream().map(TaskTypeAdapter::type).collect(Collectors.toSet()).size());
        adapters.forEach(adapter -> {
            assertEquals(TaskTypeAdapter.STANDARD_BUDGET, adapter.defaultBudget());
            assertFalse(adapter.toolCodes().isEmpty());
            assertFalse(adapter.systemPrompt().isBlank());
        });
        Set<String> toolCodes = adapters.stream().flatMap(adapter -> adapter.toolCodes().stream())
                .collect(Collectors.toSet());
        assertEquals(13, toolCodes.size());
        // 代码注册表 ⊇ 适配器声明，且注册表不再有本轮范围外的额外工具
        AgentToolRegistry registry = new AgentToolRegistry(ScopedSnapshotAgentTools.all());
        Set<String> registered = new LinkedHashSet<>();
        adapters.forEach(adapter -> adapter.toolCodes().forEach(code -> {
            assertTrue(registry.get(code) != null, "代码注册表缺工具: " + code);
            registered.add(code);
        }));
        assertEquals(registered, toolCodes);
        try (var stream = getClass().getResourceAsStream("/db/schema.sql")) {
            String schema = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Set<String>> seededTypes = parseSeedTaskTypes(schema);
            toolCodes.forEach(code -> assertTrue(seededTypes.containsKey(code),
                    "schema 缺工具种子: " + code));
            adapters.forEach(adapter -> adapter.toolCodes().forEach(code -> assertTrue(
                    seededTypes.get(code).contains(adapter.type()),
                    "schema 种子未登记类型 " + adapter.type() + " 的工具 " + code)));
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * 13/13 类型适配器注册齐全（总册 3.6.1）：注册表覆盖全量枚举、类型唯一，
     * 助手与向量两类自带预算与策略（不落入普通类型统一预算）。
     */
    @Test
    void thirteenTypeAdaptersRegisterAcrossFullEnum() {
        Set<String> expectedTypes = Set.of(
                "requirement_import", "test_design_generation", "review_selection", "plan_selection",
                "case_complete", "case_priority", "plan_order", "bug_classify", "bug_duplicate_scan",
                "bug_triage", "bug_trend_summary", "assistant_parse", "vector_reindex");
        List<TaskTypeAdapter> adapters = List.of(
                new RequirementImportTypeAdapter(), new TestDesignGenerationTypeAdapter(),
                new ReviewSelectionTypeAdapter(), new PlanSelectionTypeAdapter(),
                new CaseCompleteTypeAdapter(), new CasePriorityTypeAdapter(), new PlanOrderTypeAdapter(),
                new BugClassifyTypeAdapter(), new BugDuplicateScanTypeAdapter(),
                new BugTriageTypeAdapter(), new BugTrendSummaryTypeAdapter(),
                new AssistantParseTypeAdapter(), new VectorReindexTypeAdapter());

        assertEquals(13, adapters.size());
        assertEquals(expectedTypes, adapters.stream().map(TaskTypeAdapter::type)
                .collect(Collectors.toSet()));

        // Spring 同样按该集合装配：同 type 重复注册为装配错误，13 类全部可解析
        TaskTypeAdapterRegistry registry = new TaskTypeAdapterRegistry(adapters);
        for (String type : expectedTypes) {
            assertTrue(registry.get(type) != null, "注册表缺类型: " + type);
        }

        TaskTypeAdapter assistant = registry.get("assistant_parse");
        assertEquals(5, assistant.defaultBudget().get("maxSteps"));
        assertEquals(600, assistant.defaultBudget().get("maxDurationSeconds"));
        assertEquals(200000, assistant.defaultBudget().get("maxTokens"));
        assertEquals(5, assistant.defaultBudget().get("maxCost"));
        assertTrue(assistant.toolCodes().isEmpty());

        TaskTypeAdapter vector = registry.get("vector_reindex");
        assertEquals(500, vector.defaultBudget().get("maxSteps"));
        assertEquals(3600, vector.defaultBudget().get("maxDurationSeconds"));
        assertEquals(10000000, vector.defaultBudget().get("maxTokens"));
        assertEquals(20, vector.defaultBudget().get("maxCost"));
        assertTrue(vector.toolCodes().isEmpty());
    }

    private static Map<String, Set<String>> parseSeedTaskTypes(String schema) {
        Matcher matcher = Pattern.compile("'([a-z_]+)', '[^']*', '\\[([^\\]]*)\\]'").matcher(schema);
        Map<String, Set<String>> seeded = new java.util.HashMap<>();
        while (matcher.find()) {
            Set<String> types = new LinkedHashSet<>();
            for (String type : matcher.group(2).split(",")) {
                types.add(type.replace("\"", "").trim());
            }
            seeded.put(matcher.group(1), types);
        }
        return seeded;
    }
}
