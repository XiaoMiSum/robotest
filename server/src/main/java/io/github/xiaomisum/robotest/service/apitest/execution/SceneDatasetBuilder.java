package io.github.xiaomisum.robotest.service.apitest.execution;

import io.github.xiaomisum.robotest.framework.common.SceneStepUtil;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.ReportEntryVisitor;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.ReportEntryVisitor.ResolvedSpec;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.ReportEntryVisitor.StepOutcome;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.EnvSnapshot;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.MappedResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景（结果）数据集构建与处理器明细映射：无状态纯映射，无 Bean 依赖（测试报告详细设计 2.3）。
 * 供定时任务把多场景组织为单个顶层 TestSuite 一次执行后，按各场景子 suite 的 metadata.sceneId
 * 反查结果——只做结果映射，不落库、不改变执行记录（定时任务详细设计 4.3）。
 */
@Service
public class SceneDatasetBuilder {

    public SceneExecutionService.SceneDatasetSnapshot buildSceneDataset(ApiScene scene, EnvSnapshot env,
            MappedResult result, LocalDateTime executedAt) {
        long durationMs = result.elapsedMs() == null ? 0L : result.elapsedMs();
        List<MappedResult> children =
                result.suite() ? new ArrayList<>(result.children()) : List.of();
        List<Map<String, Object>> steps = scene.getSteps() == null ? List.of() : scene.getSteps();
        List<Map<String, Object>> stepResults = new ArrayList<>();
        int passed = 0;
        int failed = 0;
        int skipped = 0;
        boolean anyError = result.errorMessage() != null;
        int childIdx = 0;
        boolean stopOnFailure = true;
        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            if (!Boolean.TRUE.equals(SceneStepUtil.getBoolean(step, "enabled"))) {
                skipped++;
                stepResults.add(SceneExecutionSupport.skippedEntry(step));
                continue;
            }
            ResolvedSpec spec = SceneExecutionSupport.resolveSpec(step);
            if (spec.errorMessage() != null) {
                failed++;
                anyError = true;
                stepResults.add(ReportEntryVisitor.reportEntry(step, spec,
                        new StepOutcome("error", null, null, null, spec.errorMessage(), 0L, List.of())));
                if (stopOnFailure) {
                    for (int restIdx = i + 1; restIdx < steps.size(); restIdx++) {
                        skipped++;
                        stepResults.add(SceneExecutionSupport.skippedEntry(steps.get(restIdx)));
                    }
                    break;
                }
                continue;
            }
            if (childIdx < children.size()) {
                StepOutcome outcome =
                        ReportEntryVisitor.extractChildOutcome(children.get(childIdx));
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
                        stepResults.add(SceneExecutionSupport.skippedEntry(steps.get(j)));
                    }
                    break;
                }
            } else {
                failed++;
                anyError = true;
                stepResults.add(ReportEntryVisitor.reportEntry(step, spec,
                        new StepOutcome("error", null, null, null, SceneExecutionSupport.engineFailureMessage(
                                new StepOutcome(result.status(), null, null, null,
                                        result.errorMessage(),
                                        durationMs, children)), 0L, List.of())));
                if (stopOnFailure) {
                    for (int j = i + 1; j < steps.size(); j++) {
                        skipped++;
                        stepResults.add(SceneExecutionSupport.skippedEntry(steps.get(j)));
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
        dataset.put("executedAt", SceneExecutionSupport.toUtcIso(executedAt));
        dataset.put("steps", stepResults);
        dataset.put("preprocessors", ReportEntryVisitor.processorEntries(result.preprocessors()));
        dataset.put("postprocessors", ReportEntryVisitor.processorEntries(result.postprocessors()));
        return new SceneExecutionService.SceneDatasetSnapshot(dataset, reportStatus, passed, failed, skipped,
                durationMs);
    }

    /** 前置/后置处理器结果节点 → 处理器执行明细：委托 ReportEntryVisitor（接口测试域重构方案 04 §3.1.2） */
    public List<Map<String, Object>> toProcessorEntries(List<MappedResult> nodes) {
        return ReportEntryVisitor.processorEntries(nodes);
    }
}