package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.SceneStepUtil;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiImportResultRespDTO;
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
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 定时/手动触发的统一执行路径（定时任务详细设计 4.1/4.3/4.4）：
 * 测试计划任务按执行范围基于实时数据圈选场景，组织为单个顶层 TestSuite 一次执行（基础设施详细设计 4.1.2）并
 * 聚合套件报告；接口同步任务直接拉取 openapi_url 指定的 OpenAPI/Swagger JSON 文件增量同步接口；
 * 执行记录留痕 → 回写任务最近状态。定时触发以系统身份执行，手动触发以当前登录用户身份执行。
 */
@Slf4j
@Component
public class ScheduledTaskRunner {
    private static final String TYPE_TEST_PLAN = "scene_execute";
    private static final String SCOPE_ALL = "all";
    private static final String SCOPE_MODULES = "modules";
    private static final String SCOPE_SCENES = "scenes";
    private static final String SCENE_STATUS_PUBLISHED = "published";

    /** 报告名称时间戳格式（测试报告详细设计 2.1.1：任务名 + 执行时间戳） */
    private static final DateTimeFormatter NAME_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private SceneExecutionService sceneExecutionService;
    @Resource
    private EnvironmentSnapshotFactory environmentSnapshotFactory;
    @Resource
    private ApiInterfaceService apiInterfaceService;
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

    /** 手动触发场景执行后的完成监听与调度线程隔离，避免长轮询占满调度池 */
    private final ExecutorService trackerPool = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "api-test-task-tracker");
        thread.setDaemon(true);
        return thread;
    });

    public record ImportOutcome(UUID importRecordId, String status) {
    }

    /** 调度线程调用：系统身份执行，不校验成员关系（定时任务详细设计 4.3） */
    public void runTask(ApiScheduledTask task, String triggerType) {
        LocalDateTime triggeredAt = LocalDateTime.now();
        try {
            if (TYPE_TEST_PLAN.equals(task.getTaskType())) {
                executeTestPlan(task, ProjectAccessGuard.SYSTEM_OPERATOR_ID, triggerType, triggeredAt);
            } else {
                runSync(task, ProjectAccessGuard.SYSTEM_OPERATOR_ID, triggerType, triggeredAt);
            }
        } catch (Exception e) {
            recordFailure(task, triggerType, triggeredAt, e);
        }
    }

    /** 手动触发测试计划任务：单一大 Suite 整包一次执行（基础设施详细设计 4.1.2），放在 tracker 线程避免阻塞请求线程 */
    public void launchTestPlanAsyncFinalize(ApiScheduledTask task, UUID executorUserId, String triggerType) {
        LocalDateTime triggeredAt = LocalDateTime.now();
        updateTaskExecution(task.getId(), "running");
        trackerPool.submit(() -> executeTestPlan(task, executorUserId, triggerType, triggeredAt));
    }

    /** 手动触发接口同步任务：同步执行并留痕，业务异常留痕后向上抛出供前端提示 */
    public ImportOutcome runSyncRethrow(ApiScheduledTask task, UUID executorUserId, String triggerType) {
        LocalDateTime triggeredAt = LocalDateTime.now();
        try {
            return runSync(task, executorUserId, triggerType, triggeredAt);
        } catch (ServiceException e) {
            recordFailure(task, triggerType, triggeredAt, e);
            throw e;
        }
    }

    /** 上一次触发未结束时写入 skipped 记录，不重复触发（定时任务详细设计 4.1 第 4 步） */
    public void writeSkipped(ApiScheduledTask task, String triggerType) {
        insertRecord(task.getId(), task.getProjectId(), triggerType, "skipped",
                "上一次执行尚未结束，本次触发跳过", null, null, LocalDateTime.now(), 0);
    }

    // ==================== 测试计划（单一大 Suite 执行） ====================

    private void executeTestPlan(ApiScheduledTask task, UUID executorUserId, String triggerType,
            LocalDateTime triggeredAt) {
        updateTaskExecution(task.getId(), "running");
        try {
            runTestPlan(task, executorUserId, triggerType, triggeredAt);
        } catch (Exception e) {
            log.error("=====",e);
            recordFailure(task, triggerType, triggeredAt, e);
        }
    }

    /**
     * 单一大 Suite 执行（基础设施详细设计 4.1.2、定时任务详细设计 4.3）：把执行范围内的全部场景组织为
     * 一个顶层 TestSuite 一次执行——顶层 configelements 挂环境配置，每个场景一个子 TestSuite
     * （variables = 环境 + 场景，children = 场景步骤），子 suite 携带 metadata {sceneId, taskId}。
     * 整包一次 Ryze.start 后按 metadata.sceneId 从结果树递归反查各场景：逐场景生成一条执行记录 + 一份
     * 套件报告（全部场景共享 report_id）。无取消、无超时（整包一次性执行，设计 4.3）。
     */
    private void runTestPlan(ApiScheduledTask task, UUID executorUserId, String triggerType,
            LocalDateTime triggeredAt) {
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
                // 草稿场景不参与定时执行（含全量/按模块/指定场景三种范围），跳过并留痕提示
                insertRecord(task.getId(), task.getProjectId(), triggerType, "skipped",
                        "场景「" + scene.getName() + "」为草稿状态，已跳过", null, null, triggeredAt, 0);
                continue;
            }
            if (!isExecutable(scene)) {
                // 不可执行（无启用步骤）的场景跳过（设计 4.3 第 3 步）
                insertRecord(task.getId(), task.getProjectId(), triggerType, "skipped",
                        "场景「" + scene.getName() + "」不可执行（无启用步骤），已跳过", null, null, triggeredAt, 0);
                continue;
            }
            eligible.add(scene);
        }
        if (eligible.isEmpty()) {
            // 圈选范围内场景全部被跳过：无实际执行则任务状态置 failed，列表可见提示
            updateTaskLastExecution(task.getId(), "failed");
            return;
        }
        DebugRyzeConverter.EnvSnapshot env = environmentSnapshotFactory.resolve(
                task.getProjectId(), task.getEnvironmentId());

        // 组装顶层大 TestSuite：场景 = 子 TestSuite，环境内容（变量/前后置处理器/配置元件）统一挂顶层供子级继承
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("title", task.getName());
        // 顶层 suite id = taskId，供结果树/快照直接定位所属任务（定时任务详细设计 4.3）
        root.put("id", task.getId().toString());
        List<Map<String, Object>> children = new ArrayList<>();
        for (ApiScene scene : eligible) {
            // 子 suite variables 仅场景变量（不含环境），环境变量经 Ryze context chain 从顶层继承（定时任务详细设计 4.3）
            children.add(SceneRyzeConverter.buildSceneSuite(scene.getName(), env,
                    SceneRyzeConverter.buildSceneVariables(orEmpty(scene.getVariables())),
                    perStepVariables(scene), stepSpecs(scene), orEmpty(scene.getProcessors()),
                    scene.getId(), task.getId()));
        }
        root.put("children", children);
        if (!env.variables().isEmpty()) {
            root.put("variables", env.variables());
        }
        List<Map<String, Object>> configElements = SceneRyzeConverter.buildConfigureElements(env);
        if (!configElements.isEmpty()) {
            root.put("configelements", configElements);
        }
        if (!env.preprocessors().isEmpty()) {
            root.put("preprocessors", env.preprocessors());
        }
        if (!env.postprocessors().isEmpty()) {
            root.put("postprocessors", env.postprocessors());
        }

        io.github.xiaomisum.ryze.Result result;
        try {
            result = sceneExecutionService.startSuite(root, task.getProjectId());
        } catch (Exception e) {
            recordFailure(task, triggerType, triggeredAt, e);
            return;
        }
        if (result.getThrowable() != null) {
            // 顶层 suite 构建/启动异常（ryze 捕获进 result.throwable 而非抛出）：
            // 不生成套件报告、不写逐场景执行记录，仅落一条任务级 failed 留痕（定时任务详细设计 4.3）
            Throwable throwable = result.getThrowable();
            String message = truncate(throwable.getMessage() != null
                    ? throwable.getMessage() : throwable.getClass().getSimpleName());
            insertRecord(task.getId(), task.getProjectId(), triggerType, "failed", message,
                    null, null, triggeredAt, (int) (System.currentTimeMillis() - startedAt));
            updateTaskLastExecution(task.getId(), "failed");
            return;
        }
        Map<UUID, io.github.xiaomisum.ryze.Result> byScene = new LinkedHashMap<>();
        collectSceneResults(result, byScene);

        // 逐场景结果映射 + 数据采集（全部场景共享同一 report_id，见下）
        List<Map<String, Object>> datasets = new ArrayList<>();
        List<ApiExecutionRecord> records = new ArrayList<>();
        int passedScenes = 0;
        int failedScenes = 0;
        String manualTrigger = ProjectAccessGuard.SYSTEM_OPERATOR_ID.equals(executorUserId) ? "scheduled" : "manual";
        for (ApiScene scene : eligible) {
            io.github.xiaomisum.ryze.Result sceneResult = byScene.get(scene.getId());
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

        // 套件报告（report_type=suite，external_id=任务ID）→ 回写 report_id
        ApiReport report = buildSuiteReport(task, eligible.size(), datasets, passedScenes, failedScenes,
                startedAt, triggeredAt, env, result);
        reportMapper.insert(report);
        UUID reportId = report.getId();

        for (ApiExecutionRecord record : records) {
            record.setReportId(reportId);
            executionRecordMapper.insert(record);
        }
        // 逐场景任务执行记录留痕（多场景共享同一 report_id；多次触发各成一套件报告）
        for (ApiScene scene : eligible) {
            insertRecord(task.getId(), task.getProjectId(), triggerType, "success",
                    "场景「" + scene.getName() + "」执行完成", reportId, null, triggeredAt,
                    (int) (System.currentTimeMillis() - startedAt));
        }
        updateTaskLastExecution(task.getId(), failedScenes > 0 ? "failed" : "success");
    }

    /** 沿结果树递归收集各场景子 TestSuite 结果，keyed by metadata.sceneId（定时任务详细设计 4.3） */
    private void collectSceneResults(io.github.xiaomisum.ryze.Result node,
            Map<UUID, io.github.xiaomisum.ryze.Result> byScene) {
        if (node instanceof io.github.xiaomisum.ryze.testelement.TestSuiteResult suite) {
            Object metaSceneId = suite.getMetadata() == null ? null : suite.getMetadata().get("sceneId");
            if (metaSceneId != null) {
                try {
                    byScene.putIfAbsent(UUID.fromString(metaSceneId.toString()), suite);
                } catch (IllegalArgumentException ignored) {
                    // 非 sceneId 元数据（如无来源的顶层 suite），忽略
                }
            }
            for (io.github.xiaomisum.ryze.Result child : suite.getChildren()) {
                collectSceneResults(child, byScene);
            }
        }
    }

    /** 场景执行记录：scene_id 单值（严格区分隔离场景）、共享套件 report_id、source=schedule */
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

    /** 套件报告（report_type=suite）：scenes[] 为逐场景数据集（测试报告详细设计 2.3.2、4.1） */
    private ApiReport buildSuiteReport(ApiScheduledTask task, int totalScenes,
            List<Map<String, Object>> scenes, int passedScenes, int failedScenes,
            long startedAt, LocalDateTime triggeredAt, DebugRyzeConverter.EnvSnapshot env,
            io.github.xiaomisum.ryze.Result rootResult) {
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

        // 套件报告状态判定（测试报告详细设计 4.1）：全部通过 success，含失败 failed
        String suiteStatus = failedScenes > 0 ? "failed" : "success";

        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("taskId", task.getId().toString());
        dataset.put("taskName", task.getName());
        dataset.put("source", "schedule");
        dataset.put("status", suiteStatus);
        dataset.put("summary", summary);
        dataset.put("environmentName", env == null ? null : env.name());
        dataset.put("triggeredAt", triggeredAt.toString());
        dataset.put("scenes", scenes);
        List<Map<String, Object>> envPre = rootResult == null ? List.of()
                : sceneExecutionService.toProcessorEntries(rootResult.getPreprocessors());
        dataset.put("preprocessors", envPre == null ? List.of() : envPre);
        List<Map<String, Object>> envPost = rootResult == null ? List.of()
                : sceneExecutionService.toProcessorEntries(rootResult.getPostprocessors());
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
        // 整包 Ryze 结果树快照（含各场景子 suite），供结果回溯（测试报告详细设计 2.3.3）
        report.setRyzeSnapshot(RyzeResultSnapshotConverter.toSnapshot(rootResult));
        return report;
    }

    /** 场景数据集占位：套件内场景映射缺失时兜底（步骤为空、状态 failed，避免套件丢场景） */
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
        dataset.put("executedAt", triggeredAt.toString());
        dataset.put("steps", List.of());
        dataset.put("preprocessors", List.of());
        dataset.put("postprocessors", List.of());
        return dataset;
    }

    private long num(Map<?, ?> map, String key) {
        return map.get(key) instanceof Number value ? value.longValue() : 0;
    }

    /** 报告名称（执行时固化）：任务名 + 执行时间戳（测试报告详细设计 2.1.1） */
    private String reportName(String taskName, LocalDateTime triggeredAt) {
        String base = taskName == null || taskName.isBlank() ? "测试计划" : taskName;
        return base + "-" + triggeredAt.format(NAME_STAMP);
    }

    /** 按执行范围基于实时数据圈选场景（设计 4.3 圈选规则表） */
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

    /** 指定模块及其全部子模块 id 集合（模块树按 project 一次载入，内存内遍历） */
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

    /** 场景步骤 → 仅启用步骤的 StepSpec 列表（与 perStepVariables 位置一一对应，供 buildSceneSuite 组装） */
    private List<SceneRyzeConverter.StepSpec> stepSpecs(ApiScene scene) {
        List<SceneRyzeConverter.StepSpec> specs = new ArrayList<>();
        for (Map<String, Object> step : orEmpty(scene.getSteps())) {
            if (!Boolean.TRUE.equals(step.get("enabled"))) {
                continue;
            }
            Map<String, Object> config = SceneStepUtil.getMap(step, "requestConfig");
            if (config == null || config.isEmpty()) {
                continue;
            }
            specs.add(new SceneRyzeConverter.StepSpec(
                    SceneStepUtil.getString(step, "name", null),
                    config,
                    SceneStepUtil.getList(step, "validators"),
                    SceneStepUtil.getList(step, "extractors")));
        }
        return specs;
    }

    /** 场景步骤 → 仅启用步骤的步骤级变量（与 stepSpecs 位置一一对应；步骤变量覆盖场景同名变量由 Ryze context chain 处理） */
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

    // ==================== 接口同步 ====================

    private ImportOutcome runSync(ApiScheduledTask task, UUID executorUserId, String triggerType,
            LocalDateTime triggeredAt) {
        long startedAt = System.currentTimeMillis();
        updateTaskExecution(task.getId(), "running");
        // 成员校验由 importUrl 内部承担：系统身份直通，真实用户正常校验（设计 4.4：复用导入引擎）
        ApiImportResultRespDTO result = apiInterfaceService.importUrl(
                task.getProjectId(), executorUserId, task.getOpenapiUrl(), null);
        insertRecord(task.getId(), task.getProjectId(), triggerType, "success", null,
                null, result.getImportHistoryId(), triggeredAt,
                (int) (System.currentTimeMillis() - startedAt));
        updateTaskLastExecution(task.getId(), "success");
        return new ImportOutcome(result.getImportHistoryId(), "success");
    }

    // ==================== 状态回写 ====================

    private void recordFailure(ApiScheduledTask task, String triggerType, LocalDateTime triggeredAt, Exception e) {
        String message = truncate(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        insertRecord(task.getId(), task.getProjectId(), triggerType, "failed", message,
                null, null, triggeredAt, 0);
        updateTaskLastExecution(task.getId(), "failed");
    }

    void updateTaskLastExecution(UUID taskId, String status) {
        updateTaskExecution(taskId, status);
    }

    private void updateTaskExecution(UUID taskId, String status) {
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
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }

    @PreDestroy
    void shutdown() {
        trackerPool.shutdownNow();
    }

}
