package io.github.xiaomisum.robotest.service.apitest;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.StepSpec;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.EnvironmentSnapshotProvider;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.EnvSnapshot;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.common.SceneStepUtil;
import io.github.xiaomisum.robotest.framework.config.ApiTestProperties;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneDraftExecuteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneExecuteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepDebugReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepDraftDebugReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiChangeHistoryItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiExecutionCancelRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiExecutionHistoryItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiExecutionStartRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiExecutionStatusRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneDraftExecuteRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneStepDebugRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiChangeHistory;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiReport;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiChangeHistoryMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiReportMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.DebugRyzeConverter;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.ReportEntryVisitor;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.ReportEntryVisitor.ResolvedSpec;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.ReportEntryVisitor.StepOutcome;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.RyzeResultAdapter;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.RyzeResultSnapshotConverter;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.SceneRyzeConverter;
import io.github.xiaomisum.ryze.Ryze;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.util.JsonUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 场景异步执行引擎（测试场景详细设计 4.4/4.6、基础设施详细设计 3.2）。
 * <p>
 * 单套件架构：所有步骤作为同一个 TestSuite 的 children，extractor 结果通过
 * Ryze context chain 自动流向下序步骤。场景执行语义固定为停止运行：某步骤
 * 失败时后序步骤仍会执行（Ryze 无内置中止机制），但在报告中标记为 skipped。
 */
@Slf4j
@Service
public class SceneExecutionServiceImpl implements SceneExecutionService {

    private static final String TARGET_TYPE_SCENE = "scene";

    /** 报告名称时间戳格式（测试报告详细设计 2.1.1：场景名 + 执行时间戳） */
    private static final DateTimeFormatter NAME_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private ApiSceneMapper sceneMapper;
    @Resource
    private ApiExecutionRecordMapper executionRecordMapper;
    @Resource
    private ApiReportMapper reportMapper;
    @Resource
    private ApiChangeHistoryMapper changeHistoryMapper;
    @Resource
    private SysUserMapper userMapper;
    @Resource
    private ProjectAccessGuard projectAccessGuard;
    @Resource(name = "apiTestExecutor")
    private ThreadPoolTaskExecutor apiTestExecutor;
    @Resource
    private ApiTestProperties properties;
    @Resource
    private EnvironmentSnapshotProvider environmentSnapshotFactory;
    @Resource
    private CustomFunctionRuntime functionRuntime;

    /** 运行中执行的取消标志；终态后清理 */
    private final ConcurrentHashMap<UUID, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    // ========== 异步执行 ==========

    @Override
    public ApiExecutionStartRespDTO execute(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId,
            ApiSceneExecuteReqDTO reqDTO) {
        // 约定：请求体为可选（详细设计 3.6.1），缺省时按场景默认配置执行
        ApiSceneExecuteReqDTO req = reqDTO != null ? reqDTO : new ApiSceneExecuteReqDTO();
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiScene scene = requireScene(projectId, sceneId);
        if (scene.getSteps() == null || scene.getSteps().isEmpty()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED, "场景没有可执行步骤");
        }

        ApiExecutionRecord record = new ApiExecutionRecord();
        record.setId(UUID.randomUUID());
        record.setProjectId(projectId);
        record.setSceneId(sceneId);
        record.setEnvironmentId(req.getEnvironmentId());
        record.setExecutionMode("platform");
        record.setStatus("pending");
        record.setTriggerType(req.getTriggerType() == null || req.getTriggerType().isBlank()
                ? "manual" : req.getTriggerType());
        record.setSource(req.getSource() == null || req.getSource().isBlank()
                ? "scene" : req.getSource());
        record.setExecutedAt(LocalDateTime.now());

        try {
            // 先落 pending 再入队：任务启动时记录必须已存在
            executionRecordMapper.insert(record);
            apiTestExecutor.execute(() -> run(record.getId(), userId));
        } catch (RejectedExecutionException ex) {
            executionRecordMapper.deleteById(record.getId());
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_EXECUTOR_BUSY);
        } catch (RuntimeException ex) {
            executionRecordMapper.deleteById(record.getId());
            throw ex;
        }
        return ApiExecutionStartRespDTO.builder()
                .executionId(record.getId().toString())
                .status("pending")
                .build();
    }

    /** 工作线程：pending → running → 终态；异常兜底置 error，避免轮询悬挂 */
    private void run(UUID executionId, UUID userId) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        cancelFlags.put(executionId, cancelled);
        ApiExecutionRecord carrier = new ApiExecutionRecord();
        carrier.setId(executionId);
        carrier.setStatus("running");
        try {
            RunContext ctx = loadContext(executionId);
            executionRecordMapper.updateById(carrier);
            doRun(ctx, cancelled);
        } catch (Exception ex) {
            log.warn("[api-scene] 执行 {} 异常", executionId, ex);
            ApiExecutionRecord failed = new ApiExecutionRecord();
            failed.setId(executionId);
            failed.setStatus("error");
            failed.setErrorMessage(truncate(ex.getMessage() == null ? "执行失败" : ex.getMessage(), 2000));
            executionRecordMapper.updateById(failed);
        } finally {
            cancelFlags.remove(executionId);
        }
    }

    private RunContext loadContext(UUID executionId) {
        ApiExecutionRecord record = executionRecordMapper.selectById(executionId);
        ApiScene scene = sceneMapper.selectById(record.getSceneId());
        List<Map<String, Object>> steps = scene.getSteps() == null ? List.of() : scene.getSteps();
        List<Map<String, Object>> sceneVariables =
                scene.getVariables() == null ? List.of() : scene.getVariables();
        List<Map<String, Object>> sceneProcessors =
                scene.getProcessors() == null ? List.of() : scene.getProcessors();
        EnvSnapshot env =
                environmentSnapshotFactory.resolve(record.getProjectId(), record.getEnvironmentId());
        return new RunContext(record, scene, steps, sceneVariables, env, sceneProcessors);
    }

    private void doRun(RunContext ctx, AtomicBoolean cancelled) {
        long start = System.currentTimeMillis();
        boolean stopOnFailure = true;
        int passed = 0;
        int failed = 0;
        int skipped = 0;
        boolean anyError = false;
        boolean wasCancelled = false;
        List<Map<String, Object>> stepResults = new ArrayList<>();

        // 构建所有步骤的 StepSpec 和 sampler 级变量
        List<StepSpec> allSpecs = new ArrayList<>();
        List<Map<String, Object>> perStepVars = new ArrayList<>();
        List<Map<String, Object>> enabledSteps = new ArrayList<>();
        for (Map<String, Object> step : ctx.steps()) {
            if (cancelled.get()) {
                wasCancelled = true;
                skipped++;
                stepResults.add(skippedEntry(step));
                continue;
            }
            if (!Boolean.TRUE.equals(SceneStepUtil.getBoolean(step, "enabled"))) {
                skipped++;
                stepResults.add(skippedEntry(step));
                continue;
            }
            ResolvedSpec spec = resolveSpec(step);
            if (spec.errorMessage() != null) {
                stepResults.add(ReportEntryVisitor.reportEntry(step, spec,
                        new StepOutcome("error", null, null, null, spec.errorMessage(), 0L, List.of())));
                failed++;
                anyError = true;
                // 步骤失败时后续步骤跳过（场景执行语义固定为停止运行）
                if (stopOnFailure) {
                    for (int restIdx = ctx.steps().indexOf(step) + 1; restIdx < ctx.steps().size(); restIdx++) {
                        skipped++;
                        stepResults.add(skippedEntry(ctx.steps().get(restIdx)));
                    }
                    break;
                }
                continue;
            }
            allSpecs.add(spec.spec());
            perStepVars.add(buildStepVariables(ctx.sceneVariables(), step));
            enabledSteps.add(step);
        }

        // 构建单套件：suite.variables = 环境 + 场景，sampler.variables = 步骤级
        Map<String, Object> suiteVariables = SceneRyzeConverter.buildSuiteVariables(
                ctx.env(), ctx.sceneVariables());
        Map<String, Object> suite = SceneRyzeConverter.buildSuite(
                ctx.scene().getName(), ctx.env(), suiteVariables, perStepVars, allSpecs,
                ctx.sceneProcessors());

        // 执行单套件，extractor 结果通过 Ryze context chain 流向下序步骤
        StepOutcome suiteOutcome = runSingle(suite, ctx.record().getProjectId());

        // 从套件结果中提取各步骤的 SampleResult
        List<io.github.xiaomisum.ryze.Result> children = suiteOutcome.sampleResults();
        int childIdx = 0;
        for (int i = 0; i < enabledSteps.size(); i++) {
            Map<String, Object> step = enabledSteps.get(i);
            if (childIdx < children.size()) {
                io.github.xiaomisum.ryze.Result childResult = children.get(childIdx);
                StepOutcome outcome = ReportEntryVisitor.extractChildOutcome(childResult, maxResponseBodyChars());
                anyError |= "error".equals(outcome.status());
                if ("success".equals(outcome.status())) {
                    passed++;
                } else {
                    failed++;
                }
                stepResults.add(ReportEntryVisitor.reportEntry(step,
                        new ResolvedSpec(allSpecs.get(i), null), outcome));
                childIdx++;
                // 步骤失败时首个非成功结果标记后续为 skipped
                if (stopOnFailure && !"success".equals(outcome.status())) {
                    for (int j = i + 1; j < enabledSteps.size(); j++) {
                        skipped++;
                        stepResults.add(skippedEntry(enabledSteps.get(j)));
                    }
                    break;
                }
            } else {
                // 引擎提前终止（超时/异常/取消）未再产出样本：逐步骤兜底写入，防止落"空成功"报告丢步骤
                boolean terminatedByCancel = wasCancelled || cancelled.get();
                stepResults.add(unproducedStepEntry(step, terminatedByCancel, suiteOutcome));
                if (terminatedByCancel) {
                    skipped++;
                    continue;
                }
                failed++;
                anyError = true;
                // 失败语义固定停止运行：首个漏检步骤记 error，其余标记 skipped
                if (stopOnFailure) {
                    for (int j = i + 1; j < enabledSteps.size(); j++) {
                        skipped++;
                        stepResults.add(skippedEntry(enabledSteps.get(j)));
                    }
                }
                break;
            }
        }

        finishExecution(ctx, stepResults, passed, failed, skipped, anyError, wasCancelled,
                System.currentTimeMillis() - start, suiteOutcome.rootResult());
    }

    /** 前置/后置处理器结果节点 → 处理器执行明细：委托 ReportEntryVisitor（接口测试域重构方案 04 §3.1.2） */
    @Override
    public List<Map<String, Object>> toProcessorEntries(List<io.github.xiaomisum.ryze.Result> nodes) {
        return ReportEntryVisitor.processorEntries(nodes, maxResponseBodyChars());
    }

    /** 响应体截断上限（数据集/处理器明细共用，避免调用方各自携带） */
    private int maxResponseBodyChars() {
        return properties.getDebug().getMaxResponseBodyChars();
    }

    private StepOutcome runSingle(Map<String, Object> suite, UUID projectId) {
        long guardMs = properties.getDebug().getDefaultTimeoutMs() + 5000L;
        try {
            // 执行前注入自定义函数：重写调用名并标记项目上下文
            functionRuntime.prepareSuite(suite, projectId);
            var result = apiTestExecutor.submit(() -> Ryze.start(suite))
                    .get(guardMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            return extractSuiteOutcome(result);
        } catch (java.util.concurrent.TimeoutException ex) {
            return new StepOutcome("timeout", null, null, null, "步骤执行超时(" + guardMs + "ms)", guardMs,
                    List.of());
        } catch (Exception ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            return new StepOutcome("error", null, null, null,
                    cause.getMessage() == null ? "步骤执行失败" : cause.getMessage(), 0L, List.of());
        }
    }

    /**
     * 同步执行一个（大）TestSuite 并返回原始 Ryze 结果树（不设超时，等待完整执行结束）。
     * <p>供定时任务测试计划把全部场景组织为单个顶层 TestSuite 一次执行后，按场景子 suite 的
     * metadata.sceneId 从结果树反查各场景结果（定时任务详细设计 4.3）。调用方需自行捕获异常。
     */
    @Override
    public io.github.xiaomisum.ryze.Result startSuite(Map<String, Object> suite, UUID projectId) throws Exception {
        functionRuntime.prepareSuite(suite, projectId);
        return apiTestExecutor.submit(() -> Ryze.start(suite)).get();
    }

    private StepOutcome extractSuiteOutcome(io.github.xiaomisum.ryze.Result suiteResult) {
        List<io.github.xiaomisum.ryze.Result> children =
                suiteResult instanceof io.github.xiaomisum.ryze.testelement.TestSuiteResult suite
                        ? new ArrayList<>(suite.getChildren()) : List.of();
        Long elapsed = ReportEntryVisitor.elapsedMillis(suiteResult.getStartTime(), suiteResult.getEndTime());
        Throwable error = suiteResult.getThrowable();
        return new StepOutcome(RyzeResultAdapter.resolveStepStatus(suiteResult), null, null, null,
                RyzeResultAdapter.errorMessage(error), elapsed, null, children, null, List.of(), List.of(),
                suiteResult);
    }

    private ResolvedSpec resolveSpec(Map<String, Object> step) {
        Map<String, Object> config = SceneStepUtil.getMap(step, "requestConfig");
        String name = SceneStepUtil.getString(step, "name", null);
        if (config == null || config.isEmpty()) {
            return new ResolvedSpec(null, "步骤缺少请求配置");
        }
        return new ResolvedSpec(new StepSpec(name,
                config, orEmpty(SceneStepUtil.getList(step, "validators")),
                orEmpty(SceneStepUtil.getList(step, "extractors"))), null);
    }

    private void finishExecution(RunContext ctx, List<Map<String, Object>> stepResults,
            int passed, int failed, int skipped, boolean anyError, boolean wasCancelled, long durationMs,
            io.github.xiaomisum.ryze.Result rootResult) {
        String status = anyError ? "error" : "failed";
        if (wasCancelled) {
            status = "cancelled";
        } else if (failed == 0 && !anyError) {
            status = "success";
        }

        // 报告状态口径（基础设施详细设计 2.1.4）：全部通过 success，含引擎异常 failed，部分失败 partial
        String reportStatus = switch (status) {
            case "success" -> "success";
            case "error" -> "failed";
            default -> "partial";
        };
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", stepResults.size());
        summary.put("passed", passed);
        summary.put("failed", failed);
        summary.put("skipped", skipped);
        summary.put("durationMs", durationMs);

        // 场景数据集（测试报告详细设计 2.3.1）：场景报告 result 的平面化结构
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("sceneId", ctx.scene().getId().toString());
        dataset.put("sceneName", ctx.scene().getName());
        dataset.put("status", reportStatus);
        dataset.put("summary", summary);
        dataset.put("environmentName", ctx.env().name());
        dataset.put("executedAt", toUtcIso(ctx.record().getExecutedAt()));
        dataset.put("steps", stepResults);
        dataset.put("preprocessors", rootResult == null ? List.of()
                : ReportEntryVisitor.processorEntries(rootResult.getPreprocessors(), maxResponseBodyChars()));
        dataset.put("postprocessors", rootResult == null ? List.of()
                : ReportEntryVisitor.processorEntries(rootResult.getPostprocessors(), maxResponseBodyChars()));

        ApiReport report = new ApiReport();
        report.setId(UUID.randomUUID());
        report.setProjectId(ctx.record().getProjectId());
        report.setExecutionRecordId(ctx.record().getId());
        report.setReportType("scene");
        report.setExternalId(ctx.scene().getId());
        report.setName(reportName(ctx.scene().getName(), ctx.record().getExecutedAt()));
        report.setEnvironmentName(ctx.env().name());
        report.setExecutionMode(ctx.record().getExecutionMode());
        report.setSource(ctx.record().getSource() == null ? "scene" : ctx.record().getSource());
        report.setStatus(reportStatus);
        report.setSummary(summary);
        report.setResult(dataset);
        // Ryze 结果树快照：执行结果整体序列化落库，供结果回溯（测试报告详细设计 2.3.3）
        report.setRyzeSnapshot(RyzeResultSnapshotConverter.toSnapshot(rootResult));

        ApiExecutionRecord carrier = new ApiExecutionRecord();
        carrier.setId(ctx.record().getId());
        carrier.setStatus(status);
        carrier.setDurationMs((int) Math.min(durationMs, Integer.MAX_VALUE));
        carrier.setReportId(report.getId());
        if ("error".equals(status)) {
            carrier.setErrorMessage(lastErrorMessage(stepResults));
        }

        // 报告与终态同批写入：轮询见到终态时报告必然可查
        reportMapper.insert(report);
        executionRecordMapper.updateById(carrier);
    }

    /** 报告名称（执行时固化）：场景名 + 执行时间戳（测试报告详细设计 2.1.1） */
    private String reportName(String sceneName, LocalDateTime executedAt) {
        LocalDateTime stamp = executedAt == null ? LocalDateTime.now() : executedAt;
        return sceneName + "-" + stamp.format(NAME_STAMP);
    }

    /** 时间口径：对外一律下发 ISO-8601 UTC 墙钟字符串（docs/spec 时间约定），前端按浏览器时区还原 */
    private String toUtcIso(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private String lastErrorMessage(List<Map<String, Object>> stepResults) {
        return stepResults.stream()
                .filter(entry -> !"success".equals(entry.get("status")) && entry.get("errorMessage") != null)
                .map(entry -> entry.get("errorMessage").toString())
                .reduce((first, second) -> second)
                .orElse(null);
    }

    /**
     * 由单个场景子 TestSuite 的结果节点构建场景数据集（定时任务详细设计 4.3，测试报告详细设计 2.3.1）。
     * 用于测试计划任务把多场景组织为单个顶层 TestSuite 一次执行后，按各场景子 suite 的 metadata.sceneId
     * 反查结果——不再走单场景异步执行 → 单独读取场景报告 → 调度侧拼接的旧路径。
     * 只做结果映射，不落库、不改变执行记录。
     *
     * @param scene        平台场景（步骤与名称快照来源）
     * @param env          目标环境快照（environmentName 快照来源）
     * @param result       该场景子 TestSuite 的执行结果节点（其 children 为该场景的 SampleResult）
     * @param executedAt   本套件触发/执行时间（executedAt 快照）
     */
    @Override
    public SceneDatasetSnapshot buildSceneDataset(ApiScene scene, EnvSnapshot env,
            io.github.xiaomisum.ryze.Result result, LocalDateTime executedAt) {
        long durationMs = result.getStartTime() == null || result.getEndTime() == null ? 0L
                : Duration.between(result.getStartTime(), result.getEndTime()).toMillis();
        List<io.github.xiaomisum.ryze.Result> children = result instanceof io.github.xiaomisum.ryze.testelement.TestSuiteResult suite
                ? new ArrayList<>(suite.getChildren()) : List.of();
        List<Map<String, Object>> steps = scene.getSteps() == null ? List.of() : scene.getSteps();
        List<Map<String, Object>> stepResults = new ArrayList<>();
        int passed = 0;
        int failed = 0;
        int skipped = 0;
        boolean anyError = result.getThrowable() != null;
        int childIdx = 0;
        boolean stopOnFailure = true;
        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            if (!Boolean.TRUE.equals(SceneStepUtil.getBoolean(step, "enabled"))) {
                skipped++;
                stepResults.add(skippedEntry(step));
                continue;
            }
            ResolvedSpec spec = resolveSpec(step);
            if (spec.errorMessage() != null) {
                failed++;
                anyError = true;
                stepResults.add(ReportEntryVisitor.reportEntry(step, spec,
                        new StepOutcome("error", null, null, null, spec.errorMessage(), 0L, List.of())));
                if (stopOnFailure) {
                    for (int restIdx = i + 1; restIdx < steps.size(); restIdx++) {
                        skipped++;
                        stepResults.add(skippedEntry(steps.get(restIdx)));
                    }
                    break;
                }
                continue;
            }
            if (childIdx < children.size()) {
                StepOutcome outcome = ReportEntryVisitor.extractChildOutcome(children.get(childIdx),
                        maxResponseBodyChars());
                anyError |= "error".equals(outcome.status());
                if ("success".equals(outcome.status())) {
                    passed++;
                } else {
                    failed++;
                }
                stepResults.add(ReportEntryVisitor.reportEntry(step, spec, outcome));
                childIdx++;
                if (stopOnFailure && !"success".equals(outcome.status())) {
                    for (int j = i + 1; j < steps.size(); j++) {
                        skipped++;
                        stepResults.add(skippedEntry(steps.get(j)));
                    }
                    break;
                }
            } else {
                failed++;
                anyError = true;
                stepResults.add(ReportEntryVisitor.reportEntry(step, spec,
                        new StepOutcome("error", null, null, null, engineFailureMessage(
                                new StepOutcome(RyzeResultAdapter.resolveStepStatus(result), null, null, null,
                                        RyzeResultAdapter.errorMessage(result.getThrowable()),
                                        durationMs, children)), 0L, List.of())));
                if (stopOnFailure) {
                    for (int j = i + 1; j < steps.size(); j++) {
                        skipped++;
                        stepResults.add(skippedEntry(steps.get(j)));
                    }
                }
                break;
            }
        }

        String status = anyError ? "error" : "failed";
        if (failed == 0 && !anyError) {
            status = "success";
        }
        // 报告状态口径（基础设施详细设计 2.1.4）：全部通过 success，含引擎异常 failed，部分失败 partial
        String reportStatus = switch (status) {
            case "success" -> "success";
            case "error" -> "failed";
            default -> "partial";
        };
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", stepResults.size());
        summary.put("passed", passed);
        summary.put("failed", failed);
        summary.put("skipped", skipped);
        summary.put("durationMs", durationMs);

        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("sceneId", scene.getId().toString());
        dataset.put("sceneName", scene.getName());
        dataset.put("status", reportStatus);
        dataset.put("summary", summary);
        dataset.put("environmentName", env == null ? null : env.name());
        dataset.put("executedAt", toUtcIso(executedAt));
        dataset.put("steps", stepResults);
        dataset.put("preprocessors", ReportEntryVisitor.processorEntries(result.getPreprocessors(),
                maxResponseBodyChars()));
        dataset.put("postprocessors", ReportEntryVisitor.processorEntries(result.getPostprocessors(),
                maxResponseBodyChars()));
        return new SceneDatasetSnapshot(dataset, reportStatus, passed, failed, skipped, durationMs);
    }

    // ========== 轮询 / 取消 ==========

    @Override
    public ApiExecutionStatusRespDTO getStatus(UUID workspaceId, UUID projectId, UUID userId, UUID executionId) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiExecutionRecord record = requireRecord(projectId, executionId);
        ApiScene scene = sceneMapper.selectById(record.getSceneId());
        return ApiExecutionStatusRespDTO.builder()
                .id(record.getId().toString())
                .sceneId(record.getSceneId().toString())
                .sceneName(scene == null ? null : scene.getName())
                .status(record.getStatus())
                .executionMode(record.getExecutionMode())
                .triggerType(record.getTriggerType())
                .executedAt(record.getExecutedAt())
                .durationMs(record.getDurationMs())
                .errorMessage(record.getErrorMessage())
                .reportId(record.getReportId() == null ? null : record.getReportId().toString())
                .build();
    }

    @Override
    public ApiExecutionCancelRespDTO cancel(UUID workspaceId, UUID projectId, UUID userId, UUID executionId) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiExecutionRecord record = requireRecord(projectId, executionId);
        boolean running = "pending".equals(record.getStatus()) || "running".equals(record.getStatus());
        if (running) {
            AtomicBoolean flag = cancelFlags.get(executionId);
            if (flag != null) {
                flag.set(true);
            } else {
                // 队列积压尚未起跑：直接标记取消，任务起跑时按标志跳过全部步骤
                ApiExecutionRecord carrier = new ApiExecutionRecord();
                carrier.setId(executionId);
                carrier.setStatus("cancelled");
                executionRecordMapper.updateById(carrier);
            }
        }
        return new ApiExecutionCancelRespDTO(true);
    }

    // ========== 单步调试 ==========

    @Override
    public ApiSceneStepDebugRespDTO debugStep(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId,
            UUID stepId, ApiSceneStepDebugReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiScene scene = requireScene(projectId, sceneId);
        Map<String, Object> step = requireStep(scene.getSteps(), stepId);
        EnvSnapshot env =
                environmentSnapshotFactory.resolve(projectId, reqDTO.getEnvironmentId());

        ResolvedSpec resolved = resolveSpec(step);
        if (resolved.errorMessage() != null) {
            ApiSceneStepDebugRespDTO.StepResult error = ApiSceneStepDebugRespDTO.StepResult.builder()
                    .stepId(stepId.toString())
                    .status("error")
                    .validatorResults(List.of())
                    .extractedVariables(Map.of())
                    .build();
            return ApiSceneStepDebugRespDTO.builder().stepResult(error).build();
        }
        Map<String, Object> suiteVars = SceneRyzeConverter.buildSuiteVariables(
                env,
                scene.getVariables() == null ? List.of() : scene.getVariables());
        Map<String, Object> stepVars = new LinkedHashMap<>();
        for (Map<String, Object> row : SceneStepUtil.getList(step, "variables")) {
            Object name = row.get("name");
            if (name != null && !name.toString().isBlank()) {
                stepVars.put(name.toString(), row.get("value"));
            }
        }
        StepOutcome outcome = runSingle(SceneRyzeConverter.buildSuite(
                SceneStepUtil.getString(step, "name", null), env, suiteVars, List.of(stepVars),
                List.of(resolved.spec()),
                scene.getProcessors() == null ? List.of() : scene.getProcessors()), projectId);
        // 请求摘要取解析后的实际配置（链接步骤为源定义最新值）
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", resolved.spec().requestConfig().getOrDefault("method", "GET"));
        request.put("url", resolved.spec().requestConfig().get("url"));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", outcome.responseStatus());
        response.put("headers", outcome.responseHeaders());
        response.put("body", outcome.responseBody() == null ? null
                : parseJsonSafely(truncate(outcome.responseBody(), properties.getDebug().getMaxResponseBodyChars())));
        response.put("errorMessage", outcome.errorMessage());
        ApiSceneStepDebugRespDTO.StepResult result = ApiSceneStepDebugRespDTO.StepResult.builder()
                .stepId(stepId.toString())
                .status(outcome.status())
                .durationMs(outcome.elapsedMs() == null ? null : outcome.elapsedMs().intValue())
                .request(request)
                .response(response)
                .validatorResults(List.of())
                .extractedVariables(Map.of())
                .build();
        return ApiSceneStepDebugRespDTO.builder().stepResult(result).build();
    }

    // ========== 草稿调试/执行（创建态未保存场景，用页面实时数据） ==========

    @Override
    public ApiSceneStepDebugRespDTO draftDebugStep(UUID workspaceId, UUID projectId, UUID userId,
            ApiSceneStepDraftDebugReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiSceneStepDraftDebugReqDTO.Step draftStep = reqDTO.getStep();
        EnvSnapshot env =
                environmentSnapshotFactory.resolve(projectId, reqDTO.getEnvironmentId());

        ResolvedSpec resolved = resolveDraftSpec(draftStep.getName(),
                draftStep.getRequestConfig(), draftStep.getValidators(), draftStep.getExtractors());
        if (resolved.errorMessage() != null) {
            return ApiSceneStepDebugRespDTO.builder().stepResult(ApiSceneStepDebugRespDTO.StepResult.builder()
                    .stepId("draft")
                    .status("error")
                    .validatorResults(List.of())
                    .extractedVariables(Map.of())
                    .build()).build();
        }
        Map<String, Object> suiteVars = SceneRyzeConverter.buildSuiteVariables(
                env, toVariableMapList(reqDTO.getSceneVariables()));
        Map<String, Object> stepVars = variablesToMaps(draftStep.getStepVariables());
        StepOutcome outcome = runSingle(SceneRyzeConverter.buildSuite(
                draftStep.getName(), env, suiteVars, List.of(stepVars), List.of(resolved.spec()),
                List.of()), projectId);
        // 单步套件取首个采样结果为响应摘要（套件级结果不含 responseStatus）
        StepOutcome sample = outcome.sampleResults().isEmpty() ? outcome
                : ReportEntryVisitor.extractChildOutcome(outcome.sampleResults().get(0), maxResponseBodyChars());
        return ApiSceneStepDebugRespDTO.builder().stepResult(ApiSceneStepDebugRespDTO.StepResult.builder()
                .stepId("draft")
                .status(outcome.status())
                .durationMs(outcome.elapsedMs() == null ? null : outcome.elapsedMs().intValue())
                .request(buildDraftRequest(resolved.spec().requestConfig()))
                .response(buildDraftResponse(sample))
                .validatorResults(List.of())
                .extractedVariables(Map.of())
                .build()).build();
    }

    @Override
    public ApiSceneDraftExecuteRespDTO draftExecute(UUID workspaceId, UUID projectId, UUID userId,
            ApiSceneDraftExecuteReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        List<ApiSceneDraftExecuteReqDTO.DraftStep> steps = reqDTO.getSteps() == null ? List.of() : reqDTO.getSteps();
        if (steps.isEmpty()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED, "场景没有可执行步骤");
        }
        EnvSnapshot env =
                environmentSnapshotFactory.resolve(projectId, reqDTO.getEnvironmentId());
        List<Map<String, Object>> sceneVariables = toVariableMapList(reqDTO.getSceneVariables());

        long start = System.currentTimeMillis();
        boolean stopOnFailure = true;
        int passed = 0;
        int failed = 0;
        int skipped = 0;
        String overall = "success";
        List<ApiSceneDraftExecuteRespDTO.StepResult> results = new ArrayList<>();

        // 组装所有启用步骤的 StepSpec 与 sampler 级变量；失效步骤预置为 skipped
        List<Integer> enabledIndexes = new ArrayList<>();
        List<StepSpec> specList = new ArrayList<>();
        List<Map<String, Object>> perStepVars = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            ApiSceneDraftExecuteReqDTO.DraftStep step = steps.get(i);
            if (!Boolean.TRUE.equals(step.getEnabled())) {
                skipped++;
                results.add(ApiSceneDraftExecuteRespDTO.StepResult.builder()
                        .status("skipped").name(step.getName()).build());
                continue;
            }
            ResolvedSpec resolved = resolveDraftSpec(step.getName(),
                    step.getRequestConfig(), step.getValidators(), step.getExtractors());
            if (resolved.errorMessage() != null) {
                failed++;
                overall = "failed";
                results.add(ApiSceneDraftExecuteRespDTO.StepResult.builder()
                        .status("error").name(step.getName())
                        .errorMessage(resolved.errorMessage()).build());
                if (stopOnFailure) {
                    results.addAll(skippedRemaining(steps, i + 1));
                    skipped = countStatus(results, "skipped");
                    break;
                }
                continue;
            }
            enabledIndexes.add(i);
            specList.add(resolved.spec());
            perStepVars.add(variablesToMaps(step.getStepVariables()));
        }

        if (!specList.isEmpty()) {
            Map<String, Object> suiteVariables = SceneRyzeConverter.buildSuiteVariables(env, sceneVariables);
            Map<String, Object> suite = SceneRyzeConverter.buildSuite(
                    reqDTO.getName() == null || reqDTO.getName().isBlank() ? "草稿场景" : reqDTO.getName(),
                    env, suiteVariables, perStepVars, specList, List.of());
            StepOutcome suiteOutcome = runSingle(suite, projectId);
            List<io.github.xiaomisum.ryze.Result> children = suiteOutcome.sampleResults();
            int childIdx = 0;
            if (suiteOutcome.errorMessage() != null) {
                overall = "error";
            }
            for (int k = 0; k < enabledIndexes.size(); k++) {
                ApiSceneDraftExecuteReqDTO.DraftStep step = steps.get(enabledIndexes.get(k));
                if (childIdx < children.size()) {
                    StepOutcome outcome = ReportEntryVisitor.extractChildOutcome(children.get(childIdx),
                            maxResponseBodyChars());
                    boolean ok = "success".equals(outcome.status());
                    if (ok) {
                        passed++;
                    } else {
                        failed++;
                        overall = "failed";
                    }
                    results.add(toDraftStepResult(step.getName(), outcome, specList.get(k)));
                    childIdx++;
                    if (stopOnFailure && !ok) {
                        results.addAll(skippedRemaining(steps, enabledIndexes.get(k) + 1));
                        skipped = countStatus(results, "skipped");
                        break;
                    }
                }
            }
        }
        return ApiSceneDraftExecuteRespDTO.builder()
                .status(overall)
                .passed(passed)
                .failed(failed)
                .skipped(skipped)
                .durationMs(System.currentTimeMillis() - start)
                .steps(results)
                .build();
    }

    /** 由 DTO 变量行转为 Ryze 变量 Map（name 为空跳过；步骤变量覆盖场景同名变量由 Ryze 处理） */
    private Map<String, Object> variablesToMaps(List<?> variables) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (variables == null) {
            return result;
        }
        for (Object item : variables) {
            Map<String, Object> row = toVariableMap(item);
            Object name = row.get("name");
            if (name != null && !name.toString().isBlank()) {
                result.put(name.toString(), row.get("value"));
            }
        }
        return result;
    }

    /** 草稿调试/执行入参的场景变量转 List<Map>，供 buildSuiteVariables 合并环境变量 */
    private List<Map<String, Object>> toVariableMapList(List<?> variables) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (variables == null) {
            return result;
        }
        for (Object item : variables) {
            Map<String, Object> row = toVariableMap(item);
            Object name = row.get("name");
            if (name != null && !name.toString().isBlank()) {
                LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", name.toString());
                entry.put("value", row.get("value"));
                result.add(entry);
            }
        }
        return result;
    }

    private ResolvedSpec resolveDraftSpec(String name, Map<String, Object> config,
            List<Map<String, Object>> validators, List<Map<String, Object>> extractors) {
        if (config == null || config.isEmpty()) {
            return new ResolvedSpec(null, "步骤缺少请求配置");
        }
        return new ResolvedSpec(new StepSpec(names(name),
                config, orEmpty(validators), orEmpty(extractors)), null);
    }

    private Map<String, Object> buildDraftRequest(Map<String, Object> config) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", config.getOrDefault("method", "GET"));
        request.put("url", config.get("url"));
        return request;
    }

    private Map<String, Object> buildDraftResponse(StepOutcome outcome) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", outcome.responseStatus());
        response.put("headers", outcome.responseHeaders());
        response.put("body", outcome.responseBody() == null ? null
                : parseJsonSafely(truncate(outcome.responseBody(),
                        properties.getDebug().getMaxResponseBodyChars())));
        response.put("errorMessage", outcome.errorMessage());
        return response;
    }

    private ApiSceneDraftExecuteRespDTO.StepResult toDraftStepResult(String name, StepOutcome outcome,
            StepSpec spec) {
        return ApiSceneDraftExecuteRespDTO.StepResult.builder()
                .status(outcome.status())
                .name(name)
                .durationMs(outcome.elapsedMs() == null ? null : outcome.elapsedMs().intValue())
                .request(buildDraftRequest(spec.requestConfig()))
                .response(buildDraftResponse(outcome))
                .errorMessage(outcome.errorMessage())
                .build();
    }

    private List<ApiSceneDraftExecuteRespDTO.StepResult> skippedRemaining(
            List<ApiSceneDraftExecuteReqDTO.DraftStep> steps, int from) {
        List<ApiSceneDraftExecuteRespDTO.StepResult> result = new ArrayList<>();
        for (int i = Math.max(0, from); i < steps.size(); i++) {
            result.add(ApiSceneDraftExecuteRespDTO.StepResult.builder()
                    .status("skipped").name(steps.get(i).getName()).build());
        }
        return result;
    }

    private int countStatus(List<ApiSceneDraftExecuteRespDTO.StepResult> results, String status) {
        return (int) results.stream().filter(r -> status.equals(r.getStatus())).count();
    }

    private String names(String name) {
        return name == null || name.isBlank() ? "步骤" : name;
    }

    // ========== 历史 ==========

    @Override
    public PageResult<ApiExecutionHistoryItemRespDTO> pageExecutions(UUID workspaceId, UUID projectId, UUID userId,
            UUID sceneId, PageParam pageParam) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireScene(projectId, sceneId);
        PageResult<ApiExecutionRecord> page = executionRecordMapper.selectPageByScene(sceneId, pageParam);
        List<ApiExecutionHistoryItemRespDTO> items = page.getList().stream().map(this::toHistoryItem).toList();
        return new PageResult<>(items, page.getTotal());
    }

    private ApiExecutionHistoryItemRespDTO toHistoryItem(ApiExecutionRecord record) {
        return ApiExecutionHistoryItemRespDTO.builder()
                .id(record.getId().toString())
                .status(record.getStatus())
                .executionMode(record.getExecutionMode())
                .triggerType(record.getTriggerType())
                .executedAt(record.getExecutedAt())
                .durationMs(record.getDurationMs())
                .reportId(record.getReportId() == null ? null : record.getReportId().toString())
                .build();
    }

    @Override
    public PageResult<ApiChangeHistoryItemRespDTO> pageChangeHistory(UUID workspaceId, UUID projectId, UUID userId,
            UUID sceneId, PageParam pageParam) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireScene(projectId, sceneId);
        PageResult<ApiChangeHistory> page =
                changeHistoryMapper.selectPageByTarget(TARGET_TYPE_SCENE, sceneId, pageParam);
        Map<UUID, String> operatorNames = loadOperatorNames(page.getList());
        List<ApiChangeHistoryItemRespDTO> items = page.getList().stream()
                .map(history -> toItem(history, operatorNames)).toList();
        return new PageResult<>(items, page.getTotal());
    }

    private Map<UUID, String> loadOperatorNames(List<ApiChangeHistory> histories) {
        List<UUID> userIds = histories.stream().map(ApiChangeHistory::getCreatedBy)
                .filter(Objects::nonNull).distinct().toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new LinkedHashMap<>();
        for (SysUser user : userMapper.selectBatchIds(userIds)) {
            names.put(user.getId(), user.getUsername());
        }
        return names;
    }

    private ApiChangeHistoryItemRespDTO toItem(ApiChangeHistory history, Map<UUID, String> operatorNames) {
        return ApiChangeHistoryItemRespDTO.builder()
                .id(history.getId().toString())
                .version(history.getVersion())
                .operatorName(operatorNames.get(history.getCreatedBy()))
                .changeType(history.getChangeType())
                .changeSummary(history.getContentDiff() == null ? null
                        : Objects.toString(history.getContentDiff().get("summary"), null))
                .contentDiff(history.getContentDiff())
                .changedAt(history.getCreatedAt())
                .build();
    }

    // ========== 内部结构 ==========

    private ApiExecutionRecord requireRecord(UUID projectId, UUID executionId) {
        ApiExecutionRecord record = executionRecordMapper.selectById(executionId);
        if (record == null || !record.getProjectId().equals(projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_EXECUTION_RECORD_NOT_FOUND);
        }
        return record;
    }

    private ApiScene requireScene(UUID projectId, UUID sceneId) {
        ApiScene scene = sceneMapper.selectById(sceneId);
        if (scene == null || !scene.getProjectId().equals(projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_NOT_FOUND);
        }
        return scene;
    }

    /** 在场景 steps 中按 id 查找步骤 map，未找到或不属于场景则抛 API_SCENE_STEP_NOT_FOUND */
    private Map<String, Object> requireStep(List<Map<String, Object>> steps, UUID stepId) {
        return SceneStepUtil.requireStep(steps, stepId);
    }

    /** 步骤级变量（sampler 级），与场景变量分离，由 Ryze context chain 自动覆盖 suite 级同名变量 */
    private Map<String, Object> buildStepVariables(List<Map<String, Object>> sceneVariables,
            Map<String, Object> step) {
        Map<String, Object> variables = new LinkedHashMap<>();
        // 步骤级变量覆盖场景同名变量
        for (Map<String, Object> row : SceneStepUtil.getList(step, "variables")) {
            Object name = row.get("name");
            if (name != null && !name.toString().isBlank()) {
                variables.put(name.toString(), row.get("value"));
            }
        }
        return variables;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toVariableMap(Object entity) {
        Map<String, Object> map = JsonUtils.parseObject(JsonUtils.toJsonString(entity), Map.class);
        map.keySet().removeIf(key -> key.equals("createdAt") || key.equals("updatedAt")
                || key.equals("deleted") || key.equals("tenantId"));
        return map;
    }

    private Map<String, Object> skippedEntry(Map<String, Object> step) {
        Map<String, Object> entry = new LinkedHashMap<>();
        UUID stepId = SceneStepUtil.getUUID(step, "id");
        entry.put("stepId", stepId != null ? stepId.toString() : null);
        entry.put("name", SceneStepUtil.getString(step, "name", null));
        entry.put("status", "skipped");
        return entry;
    }

    /**
     * 引擎提前终止（超时/异常/取消）未能产出结果的步骤兜底条目：取消记 skipped；
     * 否则记 error 并附套件级错误信息，避免执行落"空成功"报告导致前端看不到失败原因。
     */
    private Map<String, Object> unproducedStepEntry(Map<String, Object> step, boolean cancelled,
            StepOutcome suiteOutcome) {
        if (cancelled) {
            return skippedEntry(step);
        }
        return ReportEntryVisitor.reportEntry(step, new ResolvedSpec(null, null),
                new StepOutcome("error", null, null, null, engineFailureMessage(suiteOutcome), 0L, List.of()));
    }

    private String engineFailureMessage(StepOutcome suiteOutcome) {
        String message = suiteOutcome.errorMessage();
        return message == null || message.isBlank() ? "步骤执行异常，引擎未产出结果" : message;
    }

    private Object parseJsonSafely(String text) {
        try {
            return JsonUtils.parseObject(text, Object.class);
        } catch (Exception ex) {
            return text;
        }
    }

    private String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars);
    }

    private List<Map<String, Object>> orEmpty(List<Map<String, Object>> list) {
        return list == null ? List.of() : list;
    }

    private record RunContext(ApiExecutionRecord record, ApiScene scene, List<Map<String, Object>> steps,
            List<Map<String, Object>> sceneVariables, EnvSnapshot env,
            List<Map<String, Object>> sceneProcessors) {
    }
}