package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.time.UtcTime;
import io.github.xiaomisum.robotest.framework.task.DispatchableTask;
import io.github.xiaomisum.robotest.framework.task.TaskDispatchContext;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.StepSpec;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.EnvironmentSnapshotProvider;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.EnvSnapshot;
import io.github.xiaomisum.robotest.framework.common.SceneStepUtil;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiReport;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTask;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTaskExecution;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiReportMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskExecutionMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.service.apitest.execution.SceneExecutionService;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.MappedResult;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.SuiteBuilder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 测试计划任务执行器（从 ScheduledTaskRunner 提取）。
 * 按执行范围圈选场景 → 组装单一大 Suite → 一次执行 → 聚合报告。
 */
@Slf4j
@Component
public class TestPlanTaskHandler implements DispatchableTask {

    public static final String TYPE = "scene_execute";
    private static final String SCOPE_ALL = "all";
    private static final String SCOPE_MODULES = "modules";
    private static final String SCOPE_SCENES = "scenes";
    private static final String SCENE_STATUS_PUBLISHED = "published";
    private static final DateTimeFormatter NAME_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private SceneExecutionService sceneExecutionService;
    @Resource
    private EnvironmentSnapshotProvider environmentSnapshotFactory;
    @Resource
    private ApiSceneMapper sceneMapper;
    @Resource
    private ProjectModuleMapper moduleMapper;
    @Resource
    private ApiScheduledTaskMapper taskMapper;
    @Resource
    private ApiScheduledTaskExecutionMapper executionMapper;
    @Resource
    private ApiExecutionRecordMapper executionRecordMapper;
    @Resource
    private ApiReportMapper reportMapper;
    @Resource
    private SuiteBuilder suiteBuilder;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Map<String, Object> execute(TaskDispatchContext context) {
        // 由 ScheduledTaskRunner 委托调用，实际逻辑通过 executeTask 方法暴露
        return null;
    }

    /**
     * 执行测试计划：场景圈选 → Suite 组装 → Ryze 执行 → 报告聚合。
     * 从 ScheduledTaskRunner 提取，逻辑不变。
     */
    public void executeTask(ApiScheduledTask task, UUID executorUserId, String triggerType,
                            LocalDateTime triggeredAt) {
        updateTaskLastExecution(task.getId(), "running");
        long startedAt = System.currentTimeMillis();
        List<ApiScene> scenes = resolveScenes(task);
        if (scenes.isEmpty()) {
            insertRecord(task.getId(), task.getProjectId(), triggerType, "skipped",
                    "未圈选到可执行场景，本次触发未执行", null, null, triggeredAt, 0);
            updateTaskLastExecution(task.getId(), "failed");
            return;
        }
        List<ApiScene> eligible = new ArrayList<>();
        for (ApiScene scene : scenes) {
            if (!SCENE_STATUS_PUBLISHED.equals(scene.getStatus())) {
                insertRecord(task.getId(), task.getProjectId(), triggerType, "skipped",
                        "场景「" + scene.getName() + "」为草稿状态，已跳过", null, null, triggeredAt, 0);
                continue;
            }
            if (!isExecutable(scene)) {
                insertRecord(task.getId(), task.getProjectId(), triggerType, "skipped",
                        "场景「" + scene.getName() + "」不可执行（无启用步骤），已跳过", null, null, triggeredAt, 0);
                continue;
            }
            eligible.add(scene);
        }
        if (eligible.isEmpty()) {
            updateTaskLastExecution(task.getId(), "failed");
            return;
        }
        EnvSnapshot env = environmentSnapshotFactory.resolve(task.getProjectId(), task.getEnvironmentId());

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("title", task.getName());
        root.put("id", task.getId().toString());
        List<Map<String, Object>> children = new ArrayList<>();
        for (ApiScene scene : eligible) {
            children.add(suiteBuilder.buildSceneSuite(scene.getName(), env,
                    suiteBuilder.buildSceneVariables(orEmpty(scene.getVariables())),
                    perStepVariables(scene), stepSpecs(scene), orEmpty(scene.getProcessors()),
                    scene.getId(), task.getId()));
        }
        root.put("children", children);
        if (!env.variables().isEmpty()) {
            root.put("variables", env.variables());
        }
        List<Map<String, Object>> configElements = suiteBuilder.buildConfigureElements(env);
        if (!configElements.isEmpty()) {
            root.put("configelements", configElements);
        }
        if (!env.preprocessors().isEmpty()) {
            root.put("preprocessors", env.preprocessors());
        }
        if (!env.postprocessors().isEmpty()) {
            root.put("postprocessors", env.postprocessors());
        }

        MappedResult result;
        try {
            result = sceneExecutionService.startSuite(root, task.getProjectId());
        } catch (Exception e) {
            recordFailure(task, triggerType, triggeredAt, e);
            return;
        }
        if (result.throwableMessage() != null) {
            String message = truncate(result.throwableMessage());
            insertRecord(task.getId(), task.getProjectId(), triggerType, "failed", message,
                    null, null, triggeredAt, (int) (System.currentTimeMillis() - startedAt));
            updateTaskLastExecution(task.getId(), "failed");
            return;
        }
        Map<UUID, MappedResult> byScene = new LinkedHashMap<>();
        collectSceneResults(result, byScene);

        List<Map<String, Object>> datasets = new ArrayList<>();
        List<ApiExecutionRecord> records = new ArrayList<>();
        int passedScenes = 0;
        int failedScenes = 0;
        String manualTrigger = io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard.SYSTEM_OPERATOR_ID
                .equals(executorUserId) ? "scheduled" : "manual";
        for (ApiScene scene : eligible) {
            MappedResult sceneResult = byScene.get(scene.getId());
            if (sceneResult == null) {
                failedScenes++;
                datasets.add(placeholderSceneDataset(scene.getName(), triggeredAt));
                continue;
            }
            SceneExecutionService.SceneDatasetSnapshot snapshot =
                    sceneExecutionService.buildSceneDataset(scene, env, sceneResult, triggeredAt);
            if ("success".equals(snapshot.status())) {
                passedScenes++;
            } else {
                failedScenes++;
            }
            datasets.add(snapshot.dataset());
            records.add(executionRecord(task, scene, manualTrigger, snapshot, triggeredAt, null));
        }

        ApiReport report = buildSuiteReport(task, eligible.size(), datasets, passedScenes, failedScenes,
                startedAt, triggeredAt, env, result);
        reportMapper.insert(report);
        UUID reportId = report.getId();

        for (ApiExecutionRecord record : records) {
            record.setReportId(reportId);
            executionRecordMapper.insert(record);
        }
        for (ApiScene scene : eligible) {
            insertRecord(task.getId(), task.getProjectId(), triggerType, "success",
                    "场景「" + scene.getName() + "」执行完成", reportId, null, triggeredAt,
                    (int) (System.currentTimeMillis() - startedAt));
        }
        updateTaskLastExecution(task.getId(), failedScenes > 0 ? "failed" : "success");
    }

    // ==================== 以下为从 ScheduledTaskRunner 原样提取的私有方法 ====================

    private void collectSceneResults(MappedResult node, Map<UUID, MappedResult> byScene) {
        if (node.suite()) {
            Object metaSceneId = node.metadata() == null ? null : node.metadata().get("sceneId");
            if (metaSceneId != null) {
                try {
                    byScene.putIfAbsent(UUID.fromString(metaSceneId.toString()), node);
                } catch (IllegalArgumentException ignored) {
                }
            }
            for (MappedResult child : node.children()) {
                collectSceneResults(child, byScene);
            }
        }
    }

    private ApiExecutionRecord executionRecord(ApiScheduledTask task, ApiScene scene, String triggerType,
            SceneExecutionService.SceneDatasetSnapshot snapshot, LocalDateTime triggeredAt, UUID reportId) {
        ApiExecutionRecord record = new ApiExecutionRecord();
        record.setId(UUID.randomUUID());
        record.setProjectId(task.getProjectId());
        record.setSceneId(scene.getId());
        record.setEnvironmentId(task.getEnvironmentId());
        record.setExecutionMode("platform");
        record.setStatus(snapshot.status());
        record.setTriggerType(triggerType);
        record.setSource("schedule");
        record.setReportId(reportId);
        record.setExecutedAt(triggeredAt);
        record.setDurationMs((int) snapshot.durationMs());
        return record;
    }

    private ApiReport buildSuiteReport(ApiScheduledTask task, int totalScenes,
            List<Map<String, Object>> scenes, int passedScenes, int failedScenes,
            long startedAt, LocalDateTime triggeredAt, EnvSnapshot env, MappedResult rootResult) {
        int totalSteps = 0;
        int passedSteps = 0;
        int failedSteps = 0;
        int skippedSteps = 0;
        for (Map<String, Object> dataset : scenes) {
            Map<?, ?> sceneSummary = dataset.get("summary") instanceof Map<?, ?> s ? s : Map.of();
            totalSteps += num(sceneSummary, "total");
            passedSteps += num(sceneSummary, "passed");
            failedSteps += num(sceneSummary, "failed");
            skippedSteps += num(sceneSummary, "skipped");
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalScenes", totalScenes);
        summary.put("passedScenes", passedScenes);
        summary.put("failedScenes", failedScenes);
        summary.put("totalSteps", totalSteps);
        summary.put("passedSteps", passedSteps);
        summary.put("failedSteps", failedSteps);
        summary.put("skippedSteps", skippedSteps);
        summary.put("durationMs", System.currentTimeMillis() - startedAt);

        String suiteStatus = failedScenes > 0 ? "failed" : "success";

        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("taskId", task.getId().toString());
        dataset.put("taskName", task.getName());
        dataset.put("source", "schedule");
        dataset.put("status", suiteStatus);
        dataset.put("summary", summary);
        dataset.put("environmentName", env == null ? null : env.name());
        dataset.put("triggeredAt", UtcTime.toIsoFromSystemLocal(triggeredAt));
        dataset.put("scenes", scenes);
        List<Map<String, Object>> envPre = rootResult == null ? List.of()
                : sceneExecutionService.toProcessorEntries(rootResult.preprocessors());
        dataset.put("preprocessors", envPre == null ? List.of() : envPre);
        List<Map<String, Object>> envPost = rootResult == null ? List.of()
                : sceneExecutionService.toProcessorEntries(rootResult.postprocessors());
        dataset.put("postprocessors", envPost == null ? List.of() : envPost);

        ApiReport report = new ApiReport();
        report.setId(UUID.randomUUID());
        report.setProjectId(task.getProjectId());
        report.setReportType("suite");
        report.setExternalId(task.getId());
        report.setName(reportName(task.getName(), triggeredAt));
        report.setExecutionMode("platform");
        report.setSource("schedule");
        report.setStatus(suiteStatus);
        report.setSummary(summary);
        report.setResult(dataset);
        report.setRyzeSnapshot(rootResult == null ? null : rootResult.treeSnapshot());
        return report;
    }

    private Map<String, Object> placeholderSceneDataset(String sceneName, LocalDateTime triggeredAt) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", 0);
        summary.put("passed", 0);
        summary.put("failed", 0);
        summary.put("skipped", 0);
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("sceneId", null);
        dataset.put("sceneName", sceneName);
        dataset.put("status", "failed");
        dataset.put("summary", summary);
        dataset.put("environmentName", null);
        dataset.put("executedAt", UtcTime.toIsoFromSystemLocal(triggeredAt));
        dataset.put("steps", List.of());
        dataset.put("preprocessors", List.of());
        dataset.put("postprocessors", List.of());
        return dataset;
    }

    private long num(Map<?, ?> map, String key) {
        return map.get(key) instanceof Number value ? value.longValue() : 0;
    }

    private String reportName(String taskName, LocalDateTime triggeredAt) {
        String base = taskName == null || taskName.isBlank() ? "测试计划" : taskName;
        return base + "-" + triggeredAt.format(NAME_STAMP);
    }

    private List<ApiScene> resolveScenes(ApiScheduledTask task) {
        return switch (task.getExecutionScope() == null ? SCOPE_ALL : task.getExecutionScope()) {
            case SCOPE_ALL -> sceneMapper.listByProject(task.getProjectId());
            case SCOPE_MODULES -> {
                Set<UUID> moduleIds = resolveModuleSubtreeIds(task.getProjectId(), task.getModuleIds());
                yield moduleIds.isEmpty() ? List.of()
                        : sceneMapper.listByModuleIds(task.getProjectId(), moduleIds);
            }
            case SCOPE_SCENES -> {
                if (task.getSceneIds() == null || task.getSceneIds().isEmpty()) {
                    yield List.of();
                }
                yield sceneMapper.selectByIds(task.getSceneIds()).stream()
                        .filter(scene -> task.getProjectId().equals(scene.getProjectId()))
                        .toList();
            }
            default -> List.of();
        };
    }

    private Set<UUID> resolveModuleSubtreeIds(UUID projectId, List<UUID> rootIds) {
        if (rootIds == null || rootIds.isEmpty()) {
            return Set.of();
        }
        List<ProjectModule> modules = moduleMapper.listByProjectId(projectId);
        Map<UUID, List<ProjectModule>> childrenByParent = new LinkedHashMap<>();
        for (ProjectModule module : modules) {
            if (module.getParentId() != null) {
                childrenByParent.computeIfAbsent(module.getParentId(), k -> new ArrayList<>()).add(module);
            }
        }
        Set<UUID> result = new HashSet<>(rootIds);
        List<UUID> queue = new ArrayList<>(rootIds);
        while (!queue.isEmpty()) {
            UUID parentId = queue.remove(queue.size() - 1);
            for (ProjectModule child : childrenByParent.getOrDefault(parentId, List.of())) {
                if (result.add(child.getId())) {
                    queue.add(child.getId());
                }
            }
        }
        return result;
    }

    private boolean isExecutable(ApiScene scene) {
        return scene.getSteps() != null
                && scene.getSteps().stream().anyMatch(step -> Boolean.TRUE.equals(step.get("enabled")));
    }

    private List<StepSpec> stepSpecs(ApiScene scene) {
        List<StepSpec> specs = new ArrayList<>();
        for (Map<String, Object> step : orEmpty(scene.getSteps())) {
            if (!Boolean.TRUE.equals(step.get("enabled"))) {
                continue;
            }
            Map<String, Object> config = SceneStepUtil.getMap(step, "requestConfig");
            if (config == null || config.isEmpty()) {
                continue;
            }
            specs.add(new StepSpec(
                    SceneStepUtil.getString(step, "name", null),
                    config,
                    SceneStepUtil.getList(step, "validators"),
                    SceneStepUtil.getList(step, "extractors")));
        }
        return specs;
    }

    private List<Map<String, Object>> perStepVariables(ApiScene scene) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> step : orEmpty(scene.getSteps())) {
            if (!Boolean.TRUE.equals(step.get("enabled"))) {
                continue;
            }
            Map<String, Object> variables = new LinkedHashMap<>();
            for (Map<String, Object> row : SceneStepUtil.getList(step, "variables")) {
                Object name = row.get("name");
                if (name != null && !name.toString().isBlank()) {
                    variables.put(name.toString(), row.get("value"));
                }
            }
            result.add(variables);
        }
        return result;
    }

    private List<Map<String, Object>> orEmpty(List<Map<String, Object>> list) {
        return list == null ? List.of() : list;
    }

    private void recordFailure(ApiScheduledTask task, String triggerType, LocalDateTime triggeredAt, Exception e) {
        String message = truncate(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        insertRecord(task.getId(), task.getProjectId(), triggerType, "failed", message,
                null, null, triggeredAt, 0);
        updateTaskLastExecution(task.getId(), "failed");
    }

    void updateTaskLastExecution(UUID taskId, String status) {
        ApiScheduledTask update = new ApiScheduledTask();
        update.setId(taskId);
        update.setLastExecutionStatus(status);
        update.setLastExecutionAt(LocalDateTime.now());
        taskMapper.updateById(update);
    }

    private UUID insertRecord(UUID taskId, UUID projectId, String triggerType, String status,
            String errorMessage, UUID reportId, UUID importRecordId, LocalDateTime triggeredAt, Integer durationMs) {
        ApiScheduledTaskExecution record = new ApiScheduledTaskExecution();
        record.setId(UUID.randomUUID());
        record.setTaskId(taskId);
        record.setProjectId(projectId);
        record.setTriggerType(triggerType);
        record.setStatus(status);
        record.setErrorMessage(errorMessage);
        record.setReportId(reportId);
        record.setImportRecordId(importRecordId);
        record.setTriggeredAt(triggeredAt);
        record.setDurationMs(durationMs);
        executionMapper.insert(record);
        return record.getId();
    }

    private String truncate(String message) {
        return message != null && message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}
