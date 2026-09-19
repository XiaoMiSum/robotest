package io.github.xiaomisum.robotest.service.apitest.execution;

import io.github.xiaomisum.robotest.service.apitest.execution.ReportEntryVisitor.ResolvedSpec;
import io.github.xiaomisum.robotest.service.apitest.execution.ReportEntryVisitor.StepOutcome;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.common.SceneStepUtil;
import io.github.xiaomisum.robotest.framework.config.ApiTestProperties;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneDraftExecuteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepDebugReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepDraftDebugReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneDraftExecuteRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneStepDebugRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.EnvSnapshot;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.EnvironmentSnapshotProvider;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.MappedResult;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.StepSpec;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.SuiteBuilder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 场景单步调试与草稿（创建态未保存场景）调试/执行：用页面实时数据，复用引擎接缝
 * {@link SceneExecutionLauncher#runSingle}（测试场景详细设计 3.6.3/3.6.4）。
 * 自 04 §4.1 步骤 4 拆分，与异步编排、历史查询解耦。
 */
@Service
public class DraftExecutionService {

    @Resource
    private ProjectAccessGuard projectAccessGuard;
    @Resource
    private ApiSceneMapper sceneMapper;
    @Resource
    private EnvironmentSnapshotProvider environmentSnapshotFactory;
    @Resource
    private SuiteBuilder suiteBuilder;
    @Resource
    private ApiTestProperties properties;
    @Resource
    private SceneExecutionLauncher launcher;

    // ========== 单步调试 ==========

    public ApiSceneStepDebugRespDTO debugStep(UUID workspaceId, UUID projectId, UUID userId, UUID sceneId,
            UUID stepId, ApiSceneStepDebugReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiScene scene = SceneExecutionSupport.requireScene(sceneMapper, projectId, sceneId);
        Map<String, Object> step = SceneStepUtil.requireStep(scene.getSteps(), stepId);
        EnvSnapshot env =
                environmentSnapshotFactory.resolve(projectId, reqDTO.getEnvironmentId());

        ResolvedSpec resolved = SceneExecutionSupport.resolveSpec(step);
        if (resolved.errorMessage() != null) {
            ApiSceneStepDebugRespDTO.StepResult error = ApiSceneStepDebugRespDTO.StepResult.builder()
                    .stepId(stepId.toString())
                    .status("error")
                    .validatorResults(List.of())
                    .extractedVariables(Map.of())
                    .build();
            return ApiSceneStepDebugRespDTO.builder().stepResult(error).build();
        }
        Map<String, Object> suiteVars = suiteBuilder.buildSuiteVariables(
                env,
                scene.getVariables() == null ? List.of() : scene.getVariables());
        Map<String, Object> stepVars = new LinkedHashMap<>();
        for (Map<String, Object> row : SceneStepUtil.getList(step, "variables")) {
            Object name = row.get("name");
            if (name != null && !name.toString().isBlank()) {
                stepVars.put(name.toString(), row.get("value"));
            }
        }
        StepOutcome outcome = launcher.runSingle(suiteBuilder.buildSuite(
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
                : parseJsonSafely(SceneExecutionSupport.truncate(outcome.responseBody(),
                        properties.getDebug().getMaxResponseBodyChars())));
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

    public ApiSceneStepDebugRespDTO draftDebugStep(UUID workspaceId, UUID projectId, UUID userId,
            ApiSceneStepDraftDebugReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiSceneStepDraftDebugReqDTO.Step draftStep = reqDTO.getStep();
        EnvSnapshot env =
                environmentSnapshotFactory.resolve(projectId, reqDTO.getEnvironmentId());

        ResolvedSpec resolved = SceneExecutionSupport.resolveDraftSpec(draftStep.getName(),
                draftStep.getRequestConfig(), draftStep.getValidators(), draftStep.getExtractors());
        if (resolved.errorMessage() != null) {
            return ApiSceneStepDebugRespDTO.builder().stepResult(ApiSceneStepDebugRespDTO.StepResult.builder()
                    .stepId("draft")
                    .status("error")
                    .validatorResults(List.of())
                    .extractedVariables(Map.of())
                    .build()).build();
        }
        Map<String, Object> suiteVars = suiteBuilder.buildSuiteVariables(
                env, toVariableMapList(reqDTO.getSceneVariables()));
        Map<String, Object> stepVars = variablesToMaps(draftStep.getStepVariables());
        StepOutcome outcome = launcher.runSingle(suiteBuilder.buildSuite(
                draftStep.getName(), env, suiteVars, List.of(stepVars), List.of(resolved.spec()),
                List.of()), projectId);
        // 单步套件取首个采样结果为响应摘要（套件级结果不含 responseStatus）
        StepOutcome sample = outcome.sampleResults().isEmpty() ? outcome
                : ReportEntryVisitor.extractChildOutcome(outcome.sampleResults().get(0));
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
            ResolvedSpec resolved = SceneExecutionSupport.resolveDraftSpec(step.getName(),
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
            Map<String, Object> suiteVariables = suiteBuilder.buildSuiteVariables(env, sceneVariables);
            Map<String, Object> suite = suiteBuilder.buildSuite(
                    reqDTO.getName() == null || reqDTO.getName().isBlank() ? "草稿场景" : reqDTO.getName(),
                    env, suiteVariables, perStepVars, specList, List.of());
            StepOutcome suiteOutcome = launcher.runSingle(suite, projectId);
            List<MappedResult> children = suiteOutcome.sampleResults();
            int childIdx = 0;
            if (suiteOutcome.errorMessage() != null) {
                overall = "error";
            }
            for (int k = 0; k < enabledIndexes.size(); k++) {
                ApiSceneDraftExecuteReqDTO.DraftStep step = steps.get(enabledIndexes.get(k));
                if (childIdx < children.size()) {
                    StepOutcome outcome = ReportEntryVisitor.extractChildOutcome(children.get(childIdx));
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

    // ========== 草稿私有解析 ==========

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

    @SuppressWarnings("unchecked")
    private Map<String, Object> toVariableMap(Object entity) {
        Map<String, Object> map = JsonUtils.parseObject(JsonUtils.toJsonString(entity), Map.class);
        map.keySet().removeIf(key -> key.equals("createdAt") || key.equals("updatedAt")
                || key.equals("deleted") || key.equals("tenantId"));
        return map;
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
                : parseJsonSafely(SceneExecutionSupport.truncate(outcome.responseBody(),
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

    private Object parseJsonSafely(String text) {
        try {
            return JsonUtils.parseObject(text, Object.class);
        } catch (Exception ex) {
            return text;
        }
    }
}