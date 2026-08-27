package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.config.ApiTestProperties;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneExecuteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepDebugReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiChangeHistoryItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiExecutionCancelRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiExecutionHistoryItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiExecutionStartRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiExecutionStatusRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneStepDebugRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiChangeHistory;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterfaceStep;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiReport;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiSceneStep;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiSceneStepVariable;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiChangeHistoryMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceStepMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiReportMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneStepMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneStepVariableMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScenarioVariableMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.Header;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.util.JsonUtils;

import java.time.Duration;
import java.time.LocalDateTime;
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
 * Ryze context chain 自动流向下序步骤。failure_rule=all 时后序步骤仍会执行
 * （Ryze 无内置中止机制），但在报告中标记为 skipped。
 */
@Slf4j
@Service
public class SceneExecutionServiceImpl implements SceneExecutionService {

    private static final String TARGET_TYPE_SCENE = "scene";

    @Resource
    private ApiSceneMapper sceneMapper;
    @Resource
    private ApiSceneStepMapper stepMapper;
    @Resource
    private ApiSceneStepVariableMapper stepVariableMapper;
    @Resource
    private ApiScenarioVariableMapper scenarioVariableMapper;
    @Resource
    private ApiExecutionRecordMapper executionRecordMapper;
    @Resource
    private ApiReportMapper reportMapper;
    @Resource
    private ApiChangeHistoryMapper changeHistoryMapper;
    @Resource
    private ApiInterfaceStepMapper interfaceStepMapper;
    @Resource
    private SysUserMapper userMapper;
    @Resource
    private ProjectAccessGuard projectAccessGuard;
    @Resource(name = "apiTestExecutor")
    private ThreadPoolTaskExecutor apiTestExecutor;
    @Resource
    private ApiTestProperties properties;
    @Resource
    private EnvironmentSnapshotFactory environmentSnapshotFactory;
    @Resource
    private CustomFunctionRuntime functionRuntime;

    /** 运行中执行的取消标志；终态后清理 */
    private final ConcurrentHashMap<UUID, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    // ========== 异步执行 ==========

    @Override
    public ApiExecutionStartRespDTO execute(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId,
            ApiSceneExecuteReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiScene scene = requireScene(projectId, sceneId);
        if (stepMapper.listBySceneId(sceneId).isEmpty()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED, "场景没有可执行步骤");
        }

        ApiExecutionRecord record = new ApiExecutionRecord();
        record.setId(UUID.randomUUID());
        record.setProjectId(projectId);
        record.setSceneId(sceneId);
        record.setEnvironmentId(reqDTO.getEnvironmentId());
        record.setExecutionMode("platform");
        record.setStatus("pending");
        record.setTriggerType(reqDTO.getTriggerType() == null || reqDTO.getTriggerType().isBlank()
                ? "manual" : reqDTO.getTriggerType());
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
        List<ApiSceneStep> steps = stepMapper.listBySceneId(record.getSceneId());
        List<Map<String, Object>> sceneVariables = scenarioVariableMapper.listBySceneId(record.getSceneId())
                .stream().map(this::toVariableMap).toList();
        DebugRyzeConverter.EnvSnapshot env =
                environmentSnapshotFactory.resolve(record.getProjectId(), record.getEnvironmentId());
        return new RunContext(record, scene, steps, sceneVariables, env,
                "continue".equalsIgnoreCase(scene.getFailureRule()) ? "continue" : "all");
    }

    private void doRun(RunContext ctx, AtomicBoolean cancelled) {
        long start = System.currentTimeMillis();
        boolean stopOnFailure = "all".equals(ctx.failureRule());
        int passed = 0;
        int failed = 0;
        int skipped = 0;
        boolean anyError = false;
        boolean wasCancelled = false;
        List<Map<String, Object>> stepResults = new ArrayList<>();

        // 构建所有步骤的 StepSpec 和 sampler 级变量
        List<SceneRyzeConverter.StepSpec> allSpecs = new ArrayList<>();
        List<Map<String, Object>> perStepVars = new ArrayList<>();
        List<ApiSceneStep> enabledSteps = new ArrayList<>();
        for (ApiSceneStep step : ctx.steps()) {
            if (cancelled.get()) {
                wasCancelled = true;
                skipped++;
                stepResults.add(skippedEntry(step));
                continue;
            }
            if (!Boolean.TRUE.equals(step.getEnabled())) {
                skipped++;
                stepResults.add(skippedEntry(step));
                continue;
            }
            ResolvedSpec spec = resolveSpec(step);
            if (spec.errorMessage() != null) {
                stepResults.add(toReportEntry(step, spec,
                        new StepOutcome("error", null, null, null, spec.errorMessage(), 0L, List.of())));
                failed++;
                anyError = true;
                // failure_rule=all 时后续步骤跳过
                if (stopOnFailure) {
                    for (ApiSceneStep rest : ctx.steps().subList(ctx.steps().indexOf(step) + 1, ctx.steps().size())) {
                        skipped++;
                        stepResults.add(skippedEntry(rest));
                    }
                    break;
                }
                continue;
            }
            allSpecs.add(spec.spec());
            perStepVars.add(buildStepVariables(ctx.sceneVariables(), step.getId()));
            enabledSteps.add(step);
        }

        // 构建单套件：suite.variables = 环境 + 场景，sampler.variables = 步骤级
        Map<String, Object> suiteVariables = SceneRyzeConverter.buildSuiteVariables(
                ctx.env(), ctx.sceneVariables());
        Map<String, Object> suite = SceneRyzeConverter.buildSuite(
                ctx.scene().getName(), ctx.env(), suiteVariables, perStepVars, allSpecs);

        // 执行单套件，extractor 结果通过 Ryze context chain 流向下序步骤
        StepOutcome suiteOutcome = runSingle(suite, ctx.record().getProjectId());

        // 从套件结果中提取各步骤的 SampleResult
        List<io.github.xiaomisum.ryze.Result> children = suiteOutcome.sampleResults();
        int childIdx = 0;
        for (int i = 0; i < enabledSteps.size(); i++) {
            ApiSceneStep step = enabledSteps.get(i);
            if (childIdx < children.size()) {
                io.github.xiaomisum.ryze.Result childResult = children.get(childIdx);
                StepOutcome outcome = extractChildOutcome(childResult);
                anyError |= "error".equals(outcome.status());
                if ("success".equals(outcome.status())) {
                    passed++;
                } else {
                    failed++;
                }
                stepResults.add(toReportEntry(step,
                        new ResolvedSpec(allSpecs.get(i), null), outcome));
                childIdx++;
                // failure_rule=all 时首个非成功结果标记后续为 skipped
                if (stopOnFailure && !"success".equals(outcome.status())) {
                    for (int j = i + 1; j < enabledSteps.size(); j++) {
                        skipped++;
                        stepResults.add(skippedEntry(enabledSteps.get(j)));
                    }
                    break;
                }
            }
        }

        finishExecution(ctx, stepResults, passed, failed, skipped, anyError, wasCancelled,
                System.currentTimeMillis() - start);
    }

    private StepOutcome extractChildOutcome(io.github.xiaomisum.ryze.Result childResult) {
        Long elapsed = Duration.between(childResult.getStartTime(), childResult.getEndTime()).toMillis();
        Throwable error = childResult.getThrowable();
        if (childResult instanceof io.github.xiaomisum.ryze.testelement.sampler.SampleResult sample) {
            Integer responseStatus = null;
            Map<String, Object> responseHeaders = null;
            String responseBody = null;
            if (sample.getResponse() instanceof io.github.xiaomisum.ryze.protocol.http.RealHTTPResponse response) {
                responseStatus = response.status();
                responseHeaders = toHeaderMap(response.headers());
                responseBody = bytesAsString(response);
            }
            Throwable sampleError = sample.getThrowable() != null ? sample.getThrowable() : error;
            return new StepOutcome(mapStatus(sample.getStatus()), responseStatus, responseHeaders, responseBody,
                    sampleError == null ? null : sampleError.getMessage(), elapsed, List.of());
        }
        return new StepOutcome(mapStatus(childResult.getStatus()), null, null, null,
                error == null ? null : error.getMessage(), elapsed, List.of());
    }

    private StepOutcome runSingle(Map<String, Object> suite, UUID projectId) {
        long guardMs = properties.getDebug().getDefaultTimeoutMs() + 5000L;
        try {
            // 执行前注入自定义函数：重写调用名并标记项目上下文
            functionRuntime.prepareSuite(suite, projectId);
            var result = apiTestExecutor.submit(() -> io.github.xiaomisum.ryze.Ryze.start(suite))
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

    private StepOutcome extractSuiteOutcome(io.github.xiaomisum.ryze.Result suiteResult) {
        List<io.github.xiaomisum.ryze.Result> children =
                suiteResult instanceof io.github.xiaomisum.ryze.testelement.TestSuiteResult suite
                        ? new ArrayList<>(suite.getChildren()) : List.of();
        Long elapsed = Duration.between(suiteResult.getStartTime(), suiteResult.getEndTime()).toMillis();
        Throwable error = suiteResult.getThrowable();
        return new StepOutcome(mapStatus(suiteResult.getStatus()), null, null, null,
                error == null ? null : error.getMessage(), elapsed, children);
    }

    /** 链接步骤执行时拉取源定义最新版本，源已删除则降级快照（测试场景详细设计 4.5） */
    private ResolvedSpec resolveSpec(ApiSceneStep step) {
        Map<String, Object> config = step.getRequestConfig();
        if ("link".equals(step.getSourceType()) && step.getSourceId() != null) {
            ApiInterfaceStep source = interfaceStepMapper.selectById(step.getSourceId());
            if (source == null) {
                if (config == null || config.isEmpty()) {
                    return new ResolvedSpec(null, "链接源已删除且无快照，无法执行");
                }
                return new ResolvedSpec(new SceneRyzeConverter.StepSpec(step.getName(),
                        config, orEmpty(step.getValidators()), orEmpty(step.getExtractors())), null);
            }
            config = source.getRequestConfig();
        }
        if (config == null || config.isEmpty()) {
            return new ResolvedSpec(null, "步骤缺少请求配置");
        }
        return new ResolvedSpec(new SceneRyzeConverter.StepSpec(step.getName(),
                config, orEmpty(step.getValidators()), orEmpty(step.getExtractors())), null);
    }

    private void finishExecution(RunContext ctx, List<Map<String, Object>> stepResults,
            int passed, int failed, int skipped, boolean anyError, boolean wasCancelled, long durationMs) {
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
        ApiReport report = new ApiReport();
        report.setId(UUID.randomUUID());
        report.setProjectId(ctx.record().getProjectId());
        report.setExecutionRecordId(ctx.record().getId());
        report.setSceneId(ctx.scene().getId());
        report.setSceneName(ctx.scene().getName());
        report.setExecutionMode(ctx.record().getExecutionMode());
        report.setStatus(reportStatus);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", stepResults.size());
        summary.put("passed", passed);
        summary.put("failed", failed);
        summary.put("skipped", skipped);
        summary.put("durationMs", durationMs);
        report.setSummary(summary);
        // 响应体截断后落库，防止大响应撑爆 JSONB
        report.setStepResults(stepResults.stream().map(entry -> {
            Object body = entry.get("responseBody");
            if (body instanceof String text) {
                entry.put("responseBody", truncate(text, properties.getDebug().getMaxResponseBodyChars()));
            }
            return entry;
        }).toList());
        report.setShareEnabled(false);

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

    private String lastErrorMessage(List<Map<String, Object>> stepResults) {
        return stepResults.stream()
                .filter(entry -> !"success".equals(entry.get("status")) && entry.get("errorMessage") != null)
                .map(entry -> entry.get("errorMessage").toString())
                .reduce((first, second) -> second)
                .orElse(null);
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
        requireScene(projectId, sceneId);
        ApiSceneStep step = requireStep(projectId, sceneId, stepId);
        DebugRyzeConverter.EnvSnapshot env =
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
                scenarioVariableMapper.listBySceneId(sceneId).stream().map(this::toVariableMap).toList());
        Map<String, Object> stepVars = new LinkedHashMap<>();
        for (ApiSceneStepVariable row : stepVariableMapper.listByStepId(stepId)) {
            stepVars.put(row.getName(), row.getValue());
        }
        StepOutcome outcome = runSingle(SceneRyzeConverter.buildSuite(
                step.getName(), env, suiteVars, List.of(stepVars), List.of(resolved.spec())), projectId);
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
                .pipelineId(record.getPipelineId())
                .pipelineUrl(record.getPipelineUrl())
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

    private ApiSceneStep requireStep(UUID projectId, UUID sceneId, UUID stepId) {
        ApiSceneStep step = stepMapper.selectById(stepId);
        if (step == null || !step.getSceneId().equals(sceneId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_STEP_NOT_FOUND);
        }
        ApiScene scene = sceneMapper.selectById(sceneId);
        if (scene == null || !projectId.equals(scene.getProjectId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_STEP_NOT_FOUND);
        }
        return step;
    }

    /** 步骤级变量（sampler 级），与场景变量分离，由 Ryze context chain 自动覆盖 suite 级同名变量 */
    private Map<String, Object> buildStepVariables(List<Map<String, Object>> sceneVariables, UUID stepId) {
        Map<String, Object> variables = new LinkedHashMap<>();
        // 步骤级变量覆盖场景同名变量
        for (ApiSceneStepVariable row : stepVariableMapper.listByStepId(stepId)) {
            variables.put(row.getName(), row.getValue());
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

    private Map<String, Object> toReportEntry(ApiSceneStep step, ResolvedSpec spec, StepOutcome outcome) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("stepId", step.getId().toString());
        entry.put("name", step.getName());
        entry.put("status", outcome.status());
        if (spec.spec() != null) {
            // 请求快照取解析后的配置（链接步骤为源定义最新值），与单步调试口径一致
            Map<String, Object> config = spec.spec().requestConfig();
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("method", config.getOrDefault("method", "GET"));
            request.put("url", config.get("url"));
            request.put("headers", config.get("headers"));
            request.put("body", config.get("body"));
            entry.put("request", request);
        }
        entry.put("responseStatus", outcome.responseStatus());
        entry.put("responseHeaders", outcome.responseHeaders());
        entry.put("responseBody", outcome.responseBody());
        entry.put("errorMessage", outcome.errorMessage());
        entry.put("durationMs", outcome.elapsedMs());
        // Ryze SampleResult 无断言级明细，验证器仅落配置供报告展示，通过与否以步骤状态为准
        List<Map<String, Object>> validators = orEmpty(step.getValidators()).stream()
                .map(this::normalizeValidatorSnapshot).toList();
        if (!validators.isEmpty()) {
            entry.put("validators", validators);
        }
        return entry;
    }

    private Map<String, Object> normalizeValidatorSnapshot(Map<String, Object> validator) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (String key : List.of("id", "name", "enabled", "target", "condition", "expected", "expression")) {
            if (validator.get(key) != null) {
                snapshot.put(key, validator.get(key));
            }
        }
        return snapshot;
    }

    private Map<String, Object> skippedEntry(ApiSceneStep step) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("stepId", step.getId().toString());
        entry.put("name", step.getName());
        entry.put("status", "skipped");
        return entry;
    }

    private String mapStatus(io.github.xiaomisum.ryze.TestStatus status) {
        if (status == io.github.xiaomisum.ryze.TestStatus.passed) {
            return "success";
        }
        return status == io.github.xiaomisum.ryze.TestStatus.failed ? "failed" : "error";
    }

    private Map<String, Object> toHeaderMap(List<Header> headers) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Header header : headers) {
            map.merge(header.getName(), header.getValue(), (a, b) -> a + ", " + b);
        }
        return map;
    }

    private String bytesAsString(io.github.xiaomisum.ryze.protocol.http.RealHTTPResponse response) {
        try {
            return response.bytesAsString();
        } catch (Exception ex) {
            return response.format();
        }
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

    private record RunContext(ApiExecutionRecord record, ApiScene scene, List<ApiSceneStep> steps,
            List<Map<String, Object>> sceneVariables, DebugRyzeConverter.EnvSnapshot env, String failureRule) {
    }

    /** 步骤规格解析结果：errorMessage 非空表示无法执行 */
    private record ResolvedSpec(SceneRyzeConverter.StepSpec spec, String errorMessage) {
    }

    /** 单步骤结果切片：状态/响应/耗时/子结果 */
    private record StepOutcome(String status, Integer responseStatus, Map<String, Object> responseHeaders,
            String responseBody, String errorMessage, Long elapsedMs,
            List<io.github.xiaomisum.ryze.Result> sampleResults) {
    }
}
