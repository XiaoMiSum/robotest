package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiReport;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTask;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiReportMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 套件报告合并（GitLab 集成详细设计 4.5 / WP-7.2）：将批次 CI 组场景数据集并入本触发的
 * 套件报告（{@code report_type='suite'}、{@code source='schedule'}、{@code external_id} = 任务 ID）。
 *
 * <p>「首写 + 追加更新」两阶段：平台组已首写时按 {@code sceneId} 幂等追加，重复到达
 * 不产生重复场景；纯 CI 任务（平台组无可用场景、触发失败、平台组中途崩溃）无首写报告，
 * 由本入口创建——每次触发恒一份套件报告，二次更新不产生空洞。单场景执行（批次无任务
 * 归属）只有场景级报告，不适用本合并。
 *
 * <p>场景数据集来源：已落场景级报告的记录直接复用其 {@code result}（同为 2.3.1 数据集）；
 * 无结果记录（信封缺失、整批降级、触发失败）以占位数据集计入（status = failed、steps 空、
 * 汇总全 0，详设 28 2.3.1 / 27 失败处理）。每次合并后全量重算 summary 与 status
 * （套件层面无 partial，任一场景非 success 即 failed）。
 */
@Slf4j
@Component
public class ApiSuiteReportAppender {

    private static final String SOURCE_SCHEDULE = "schedule";
    private static final DateTimeFormatter NAME_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ApiExecutionRecordMapper executionRecordMapper;
    private final ApiReportMapper reportMapper;
    private final ApiScheduledTaskMapper scheduledTaskMapper;
    private final ApiSceneMapper sceneMapper;

    public ApiSuiteReportAppender(ApiExecutionRecordMapper executionRecordMapper,
            ApiReportMapper reportMapper, ApiScheduledTaskMapper scheduledTaskMapper,
            ApiSceneMapper sceneMapper) {
        this.executionRecordMapper = executionRecordMapper;
        this.reportMapper = reportMapper;
        this.scheduledTaskMapper = scheduledTaskMapper;
        this.sceneMapper = sceneMapper;
    }

    /**
     * 合并批次 CI 组结果到套件报告。幂等：场景数据集按 sceneId upsert，summary / status
     * 全量重算；同批次双通道先后到达结论一致。
     */
    public void append(List<ApiExecutionRecord> records) {
        ApiExecutionRecord head = records.get(0);
        UUID taskId = head.getScheduledTaskId();
        if (taskId == null) {
            return;
        }
        ApiReport suite = reportMapper.selectLatestSuiteReportSince(taskId, SOURCE_SCHEDULE, head.getCreatedAt());
        List<Map<String, Object>> ciDatasets = buildSceneDatasets(records);
        if (suite == null) {
            createSuite(taskId, head, ciDatasets);
            return;
        }
        mergeScenes(suite, ciDatasets);
    }

    // ========== 场景数据集 ==========

    /** 批次记录 → 场景数据集列表：有场景级报告复用其 result，无结果记录以占位计入 */
    private List<Map<String, Object>> buildSceneDatasets(List<ApiExecutionRecord> records) {
        Map<UUID, String> sceneNames = loadSceneNames(records);
        List<Map<String, Object>> datasets = new ArrayList<>();
        for (ApiExecutionRecord record : records) {
            if (record.getSceneId() == null) {
                continue;
            }
            Map<String, Object> dataset = null;
            if (record.getReportId() != null) {
                ApiReport sceneReport = reportMapper.selectById(record.getReportId());
                if (sceneReport != null && sceneReport.getResult() != null) {
                    dataset = sceneReport.getResult();
                }
            }
            if (dataset == null) {
                dataset = placeholderDataset(record, sceneNames.get(record.getSceneId()));
            }
            datasets.add(dataset);
        }
        return datasets;
    }

    /** 占位数据集（详设 28 2.3.1：未产出结果的场景 status = failed、steps 为空、汇总全 0） */
    private static Map<String, Object> placeholderDataset(ApiExecutionRecord record, String sceneName) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", 0);
        summary.put("passed", 0);
        summary.put("failed", 0);
        summary.put("skipped", 0);
        summary.put("durationMs", 0L);

        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("sceneId", record.getSceneId().toString());
        dataset.put("sceneName", sceneName == null ? record.getSourceRef() : sceneName);
        dataset.put("status", "failed");
        dataset.put("execEngine", "gitlab");
        dataset.put("summary", summary);
        dataset.put("environmentName", null);
        dataset.put("executedAt", null);
        dataset.put("steps", List.of());
        dataset.put("preprocessors", List.of());
        dataset.put("postprocessors", List.of());
        return dataset;
    }

    private Map<UUID, String> loadSceneNames(List<ApiExecutionRecord> records) {
        Set<UUID> sceneIds = records.stream()
                .map(ApiExecutionRecord::getSceneId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (sceneIds.isEmpty()) {
            return Map.of();
        }
        return sceneMapper.selectByIds(sceneIds).stream()
                .collect(Collectors.toMap(scene -> scene.getId(), scene -> scene.getName(), (a, b) -> a));
    }

    // ========== 追加更新 ==========

    /** 幂等追加：按 sceneId upsert（先到者保留原位），随后全量重算并部分更新三列 */
    private void mergeScenes(ApiReport suite, List<Map<String, Object>> ciDatasets) {
        Map<String, Object> result = suite.getResult() == null ? new LinkedHashMap<>() : suite.getResult();
        List<?> rawScenes = result.get("scenes") instanceof List<?> list ? list : List.of();
        List<Map<String, Object>> scenes = new ArrayList<>();
        for (Object item : rawScenes) {
            if (item instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> scene = (Map<String, Object>) map;
                scenes.add(scene);
            }
        }
        for (Map<String, Object> ciDataset : ciDatasets) {
            String sceneId = String.valueOf(ciDataset.get("sceneId"));
            int existing = -1;
            for (int i = 0; i < scenes.size(); i++) {
                if (sceneId.equals(String.valueOf(scenes.get(i).get("sceneId")))) {
                    existing = i;
                    break;
                }
            }
            if (existing >= 0) {
                scenes.set(existing, ciDataset);
            } else {
                scenes.add(ciDataset);
            }
        }
        result.put("scenes", scenes);
        SuiteAggregates aggregates = aggregate(scenes);
        // C11：载体仅携带本次变更三列，其余列不动
        ApiReport update = new ApiReport();
        update.setId(suite.getId());
        update.setStatus(aggregates.failedScenes > 0 ? "failed" : "success");
        update.setSummary(aggregates.toSummary(suite.getSummary()));
        update.setResult(result);
        reportMapper.updateById(update);
    }

    // ========== 首写（纯 CI 任务） ==========

    private void createSuite(UUID taskId, ApiExecutionRecord head, List<Map<String, Object>> ciDatasets) {
        ApiScheduledTask task = scheduledTaskMapper.selectById(taskId);
        String taskName = task == null ? "任务" : task.getName();
        // 无平台组可参照时取批次记录耗时最大值（同批并行执行，max ≈ 批时长）
        long durationMs = recordsMaxDuration(ciDatasets);
        SuiteAggregates aggregates = aggregate(ciDatasets);
        String status = aggregates.failedScenes > 0 ? "failed" : "success";
        Map<String, Object> summary = aggregates.toSummary(null);
        summary.put("durationMs", durationMs);

        LocalDateTime triggeredAt = head.getCreatedAt() == null ? LocalDateTime.now() : head.getCreatedAt();
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("reportType", "suite");
        dataset.put("taskId", taskId.toString());
        dataset.put("taskName", taskName);
        dataset.put("source", SOURCE_SCHEDULE);
        dataset.put("status", status);
        dataset.put("summary", summary);
        dataset.put("environmentName", null);
        dataset.put("triggeredAt", triggeredAt.toString());
        dataset.put("scenes", ciDatasets);
        // CI 侧不执行环境处理器（详设 27 套件组装：处理器挂平台组顶层 suite 执行一次）
        dataset.put("preprocessors", List.of());
        dataset.put("postprocessors", List.of());

        ApiReport report = new ApiReport();
        report.setId(UUID.randomUUID());
        report.setProjectId(head.getProjectId());
        report.setReportType("suite");
        report.setExternalId(taskId);
        report.setName(taskName + "-" + triggeredAt.format(NAME_STAMP));
        report.setExecutionMode("platform");
        report.setSource(SOURCE_SCHEDULE);
        report.setStatus(status);
        report.setSummary(summary);
        report.setResult(dataset);
        reportMapper.insert(report);
        log.info("[报告聚合] 纯 CI 任务套件报告首写 reportId={} taskId={}", report.getId(), taskId);
    }

    private static long recordsMaxDuration(List<Map<String, Object>> datasets) {
        long max = 0L;
        for (Map<String, Object> dataset : datasets) {
            Object duration = dataset.get("summary") instanceof Map<?, ?> summary
                    ? summary.get("durationMs") : null;
            if (duration instanceof Number number) {
                max = Math.max(max, number.longValue());
            }
        }
        return max;
    }

    // ========== 汇总重算 ==========

    /** 全量场景数据集重算套件汇总（详设 28 2.3.2 套件汇总口径） */
    private static SuiteAggregates aggregate(List<Map<String, Object>> scenes) {
        SuiteAggregates result = new SuiteAggregates();
        result.totalScenes = scenes.size();
        for (Map<String, Object> scene : scenes) {
            if ("success".equals(String.valueOf(scene.get("status")))) {
                result.passedScenes++;
            } else {
                result.failedScenes++;
            }
            if (scene.get("summary") instanceof Map<?, ?> summary) {
                result.totalSteps += intValue(summary.get("total"));
                result.passedSteps += intValue(summary.get("passed"));
                result.failedSteps += intValue(summary.get("failed"));
                result.skippedSteps += intValue(summary.get("skipped"));
            }
        }
        return result;
    }

    private static int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    /** 套件汇总聚合器：durationMs 由调用方按来源口径补充 */
    private static final class SuiteAggregates {
        private int totalScenes;
        private int passedScenes;
        private int failedScenes;
        private int totalSteps;
        private int passedSteps;
        private int failedSteps;
        private int skippedSteps;

        /** 保留既有 summary 中本次不重算的键（如 durationMs） */
        private Map<String, Object> toSummary(Map<String, Object> existing) {
            Map<String, Object> summary = new LinkedHashMap<>();
            if (existing != null) {
                summary.putAll(existing);
            }
            summary.put("totalScenes", totalScenes);
            summary.put("passedScenes", passedScenes);
            summary.put("failedScenes", failedScenes);
            summary.put("totalSteps", totalSteps);
            summary.put("passedSteps", passedSteps);
            summary.put("failedSteps", failedSteps);
            summary.put("skippedSteps", skippedSteps);
            return summary;
        }
    }
}
