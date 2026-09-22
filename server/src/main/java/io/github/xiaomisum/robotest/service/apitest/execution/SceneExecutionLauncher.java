package io.github.xiaomisum.robotest.service.apitest.execution;

import io.github.xiaomisum.robotest.service.apitest.execution.ReportEntryVisitor.ResolvedSpec;
import io.github.xiaomisum.robotest.service.apitest.execution.ReportEntryVisitor.StepOutcome;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.common.SceneStepUtil;
import io.github.xiaomisum.robotest.framework.config.ApiTestProperties;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneExecuteReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiExecutionStartRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiReport;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiReportMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.service.apitest.CustomFunctionRuntime;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.EnvSnapshot;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.EnvironmentSnapshotProvider;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.MappedResult;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.StepSpec;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.SuiteBuilder;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.SuiteRunner;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 场景异步执行编排（测试场景详细设计 4.4/4.6、基础设施详细设计 3.2）。
 * <p>
 * 单套件架构：所有步骤作为同一个 TestSuite 的 children，extractor 结果通过
 * Ryze context chain 自动流向下序步骤。场景执行语义固定为停止运行：某步骤
 * 失败时后序步骤仍会执行（Ryze 无内置中止机制），但在报告中标记为 skipped。
 * 编排语义与结果树映射自 04 §4.1 步骤 4 拆分，runSingle 包级可见供草稿编排复用。
 */
@Slf4j
@Service
public class SceneExecutionLauncher {

    /** 报告名称时间戳格式（测试报告详细设计 2.1.1：场景名 + 执行时间戳） */
    private static final DateTimeFormatter NAME_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private ApiSceneMapper sceneMapper;
    @Resource
    private ApiExecutionRecordMapper executionRecordMapper;
    @Resource
    private ApiReportMapper reportMapper;
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
    @Resource
    private SuiteRunner suiteRunner;
    @Resource
    private SuiteBuilder suiteBuilder;
    @Resource
    private ExecutionCancelRegistry cancelRegistry;

    // ========== 异步执行 ==========

    public ApiExecutionStartRespDTO execute(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId,
            ApiSceneExecuteReqDTO reqDTO) {
        // 约定：请求体为可选（详细设计 3.6.1），缺省时按场景默认配置执行
        ApiSceneExecuteReqDTO req = reqDTO != null ? reqDTO : new ApiSceneExecuteReqDTO();
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiScene scene = SceneExecutionSupport.requireScene(sceneMapper, projectId, sceneId);
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
        AtomicBoolean cancelled = cancelRegistry.register(executionId);
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
            failed.setErrorMessage(
                    SceneExecutionSupport.truncate(ex.getMessage() == null ? "执行失败" : ex.getMessage(), 2000));
            executionRecordMapper.updateById(failed);
        } finally {
            cancelRegistry.release(executionId);
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
                stepResults.add(SceneExecutionSupport.skippedEntry(step));
                continue;
            }
            if (!Boolean.TRUE.equals(SceneStepUtil.getBoolean(step, "enabled"))) {
                skipped++;
                stepResults.add(SceneExecutionSupport.skippedEntry(step));
                continue;
            }
            ResolvedSpec spec = SceneExecutionSupport.resolveSpec(step);
            if (spec.errorMessage() != null) {
                stepResults.add(ReportEntryVisitor.reportEntry(step, spec,
                        new StepOutcome("error", null, null, null, spec.errorMessage(), 0L, List.of())));
                failed++;
                anyError = true;
                // 步骤失败时后续步骤跳过（场景执行语义固定为停止运行）
                if (stopOnFailure) {
                    for (int restIdx = ctx.steps().indexOf(step) + 1; restIdx < ctx.steps().size(); restIdx++) {
                        skipped++;
                        stepResults.add(SceneExecutionSupport.skippedEntry(ctx.steps().get(restIdx)));
                    }
                    break;
                }
                continue;
            }
            allSpecs.add(spec.spec());
            perStepVars.add(buildStepVariables(step));
            enabledSteps.add(step);
        }

        // 构建单套件：suite.variables = 环境 + 场景，sampler.variables = 步骤级
        Map<String, Object> suiteVariables = suiteBuilder.buildSuiteVariables(
                ctx.env(), ctx.sceneVariables());
        Map<String, Object> suite = suiteBuilder.buildSuite(
                ctx.scene().getName(), ctx.env(), suiteVariables, perStepVars, allSpecs,
                ctx.sceneProcessors());

        // 执行单套件，extractor 结果通过 Ryze context chain 流向下序步骤
        StepOutcome suiteOutcome = runSingle(suite, ctx.record().getProjectId());

        // 从套件结果中提取各步骤的 SampleResult
        List<MappedResult> children = suiteOutcome.sampleResults();
        int childIdx = 0;
        for (int i = 0; i < enabledSteps.size(); i++) {
            Map<String, Object> step = enabledSteps.get(i);
            if (childIdx < children.size()) {
                MappedResult childResult = children.get(childIdx);
                StepOutcome outcome = ReportEntryVisitor.extractChildOutcome(childResult);
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
                        stepResults.add(SceneExecutionSupport.skippedEntry(enabledSteps.get(j)));
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
                        stepResults.add(SceneExecutionSupport.skippedEntry(enabledSteps.get(j)));
                    }
                }
                break;
            }
        }

        finishExecution(ctx, stepResults, passed, failed, skipped, anyError, wasCancelled,
                System.currentTimeMillis() - start, suiteOutcome.rootResult());
    }

    private void finishExecution(RunContext ctx, List<Map<String, Object>> stepResults,
            int passed, int failed, int skipped, boolean anyError, boolean wasCancelled, long durationMs,
            MappedResult rootResult) {
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
        dataset.put("executedAt", SceneExecutionSupport.toUtcIso(ctx.record().getExecutedAt()));
        dataset.put("steps", stepResults);
        dataset.put("preprocessors", rootResult == null ? List.of()
                : ReportEntryVisitor.processorEntries(rootResult.preprocessors()));
        dataset.put("postprocessors", rootResult == null ? List.of()
                : ReportEntryVisitor.processorEntries(rootResult.postprocessors()));

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
        // 平台结果模型快照：执行结果整体序列化落库，供结果回溯（测试报告详细设计 2.3.3）
        report.setRyzeSnapshot(rootResult == null ? null : rootResult.treeSnapshot());

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

    private String lastErrorMessage(List<Map<String, Object>> stepResults) {
        return stepResults.stream()
                .filter(entry -> !"success".equals(entry.get("status")) && entry.get("errorMessage") != null)
                .map(entry -> entry.get("errorMessage").toString())
                .reduce((first, second) -> second)
                .orElse(null);
    }

    // ========== 轮询 / 取消 ==========

    /**
     * 同步执行一个（大）TestSuite 并返回平台结果模型（不设超时，等待完整执行结束）。
     * <p>供定时任务测试计划把全部场景组织为单个顶层 TestSuite 一次执行后，按场景子 suite 的
     * metadata.sceneId 从结果树反查各场景结果（定时任务详细设计 4.3）。调用方需自行捕获异常。
     */
    public MappedResult startSuite(Map<String, Object> suite, UUID projectId) throws Exception {
        functionRuntime.prepareSuite(suite, projectId);
        return apiTestExecutor.submit(() -> suiteRunner.run(suite)).get();
    }

    // ========== 引擎接缝 ==========

    /** 同步执行一个（大）TestSuite 并返回平台结果模型（默认超时守卫 +5s，取消/超时不抛错） */
    StepOutcome runSingle(Map<String, Object> suite, UUID projectId) {
        long guardMs = properties.getDebug().getDefaultTimeoutMs() + 5000L;
        try {
            // 执行前注入自定义函数：重写调用名并标记项目上下文
            functionRuntime.prepareSuite(suite, projectId);
            var result = apiTestExecutor.submit(() -> suiteRunner.run(suite))
                    .get(guardMs, TimeUnit.MILLISECONDS);
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

    private StepOutcome extractSuiteOutcome(MappedResult suiteResult) {
        List<MappedResult> children =
                suiteResult.suite() ? new ArrayList<>(suiteResult.children()) : List.of();
        return new StepOutcome(suiteResult.status(), null, null, null,
                suiteResult.errorMessage(), suiteResult.elapsedMs(), null, children, null, List.of(), List.of(),
                suiteResult);
    }

    /**
     * 引擎提前终止（超时/异常/取消）未能产出结果的步骤兜底条目：取消记 skipped；
     * 否则记 error 并附套件级错误信息，避免执行落"空成功"报告导致前端看不到失败原因。
     */
    private Map<String, Object> unproducedStepEntry(Map<String, Object> step, boolean cancelled,
            StepOutcome suiteOutcome) {
        if (cancelled) {
            return SceneExecutionSupport.skippedEntry(step);
        }
        return ReportEntryVisitor.reportEntry(step, new ResolvedSpec(null, null),
                new StepOutcome("error", null, null, null, SceneExecutionSupport.engineFailureMessage(suiteOutcome),
                        0L, List.of()));
    }

    /** 步骤级变量（sampler 级），与场景变量分离，由 Ryze context chain 自动覆盖 suite 级同名变量 */
    private Map<String, Object> buildStepVariables(Map<String, Object> step) {
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

    private record RunContext(ApiExecutionRecord record, ApiScene scene, List<Map<String, Object>> steps,
            List<Map<String, Object>> sceneVariables, EnvSnapshot env,
            List<Map<String, Object>> sceneProcessors) {
    }
}