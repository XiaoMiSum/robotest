package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.config.ApiTestProperties;
import io.github.xiaomisum.robotest.framework.time.UtcTime;
import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiGitRepo;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiReport;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiGitRepoMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiReportMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.service.apitest.execution.ReportEntryVisitor;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.RyzeEnvelope;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.RyzeEnvelopeParser;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.RyzeResultMapper;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.MappedResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * CI 结果回收编排（GitLab 集成详细设计 4.2 第 4-6 步 / 4.6，WP-7.1）：
 * 下载流水线制品 → 解析 {@code __Ryze_Native_Result__} 结果信封 → 按
 * {@code testClass#testMethod} 逐条回写执行记录与场景报告。
 *
 * <p>两通道共用本入口：CI 主动回报（completion）与流水线轮询（poll）。制品在
 * {@code after_script} 结束后才由 GitLab 上传，completion 通道遇制品缺失或下载失败
 * 一律推迟给轮询；poll 通道在流水线已终态仍取不到任何可用信封时按 BR-1.34 整批
 * 失败降级（附流水线链接），状态按详设 4.6 映射（success/failed → failed，canceled → cancelled）。
 *
 * <p>回写为逐条条件终态（仅 pending/running 生效）：进程内以单飞集合挡住同批次并发，
 * 跨通道先到者逐条定终态、后到者命中 0 行即弃；报告先插入后占位，占位落空即回删，
 * 不出现「记录已终态而报告不可查」的空洞。仓库 / 令牌 / 制品暂不可得只留痕不终态化，
 * 由轮询通道下周期自愈。
 */
@Slf4j
@Component
public class ApiGitResultCollector {

    /** 制品内结果目录（CI 模板 artifact paths，详设 4.6 输出目录行） */
    private static final String RESULT_DIR = "__Ryze_Native_Result__";
    /** 记录错误信息列宽（api_execution_record.error_message VARCHAR(2000)） */
    private static final int MAX_ERROR_CHARS = 2000;
    /** 报告名称时间戳（执行时固化，与场景报告既有口径一致） */
    private static final DateTimeFormatter NAME_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 记录终态集合（引擎详设 3.1 状态机；skipped 为缺信封记录的回收终态） */
    private static final Set<String> TERMINAL_STATUSES =
            Set.of("success", "failed", "error", "cancelled", "timeout", "skipped");

    /** 回收通道：决定制品不可得时的处置（completion 推迟、poll 降级） */
    public enum Channel {
        COMPLETION, POLL
    }

    private final ApiExecutionRecordMapper executionRecordMapper;
    private final ApiGitRepoMapper gitRepoMapper;
    private final ApiSceneMapper sceneMapper;
    private final ApiReportMapper reportMapper;
    private final GitLabApiClient gitLabApiClient;
    private final ApiGitBatchService batchService;
    private final ApiSuiteReportAppender appender;
    private final ApiTestProperties properties;
    private final Executor executor;
    private final byte[] secretKey;

    /** 回收中的批次票据：同进程两通道单飞；跨进程由逐条条件终态兜底 */
    private final Set<String> inflight = ConcurrentHashMap.newKeySet();

    public ApiGitResultCollector(ApiExecutionRecordMapper executionRecordMapper,
            ApiGitRepoMapper gitRepoMapper, ApiSceneMapper sceneMapper, ApiReportMapper reportMapper,
            GitLabApiClient gitLabApiClient, ApiGitBatchService batchService,
            ApiSuiteReportAppender appender, ApiTestProperties properties,
            @Qualifier("apiGitCollectExecutor") Executor executor,
            @Value("${robotest.env.secret-key:}") String base64SecretKey) {
        this.executionRecordMapper = executionRecordMapper;
        this.gitRepoMapper = gitRepoMapper;
        this.sceneMapper = sceneMapper;
        this.reportMapper = reportMapper;
        this.gitLabApiClient = gitLabApiClient;
        this.batchService = batchService;
        this.appender = appender;
        this.properties = properties;
        this.executor = executor;
        this.secretKey = SecretCryptoUtil.parseKey(base64SecretKey);
    }

    /**
     * 异步回收：回写不阻塞触发线程（详设 4.2 第 6 步），失败只留痕，
     * 交由轮询通道下周期兜底重试。
     */
    public void collectAsync(String batchId, Channel channel, String sourceStatus) {
        try {
            executor.execute(() -> {
                try {
                    collect(batchId, channel, sourceStatus);
                } catch (Exception e) {
                    log.warn("[结果回收] 回收异常，交由轮询通道兜底 batchId={}：{}", batchId, e.getMessage());
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("[结果回收] 回收任务被拒，交由轮询通道兜底 batchId={}：{}", batchId, e.getMessage());
        }
    }

    /** 同步回收入口（测试接缝；生产一律经 {@link #collectAsync}） */
    void collect(String batchId, Channel channel, String sourceStatus) {
        if (!inflight.add(batchId)) {
            log.info("[结果回收] 同批次回收进行中，本次跳过 batchId={}", batchId);
            return;
        }
        try {
            doCollect(batchId, channel, sourceStatus);
        } finally {
            inflight.remove(batchId);
        }
    }

    private void doCollect(String batchId, Channel channel, String sourceStatus) {
        List<ApiExecutionRecord> records = executionRecordMapper.selectByBatchId(batchId);
        if (records.isEmpty()) {
            log.warn("[结果回收] 批次无执行记录，跳过 batchId={}", batchId);
            return;
        }
        ApiExecutionRecord head = records.get(0);
        if (records.stream().allMatch(r -> TERMINAL_STATUSES.contains(r.getStatus()))) {
            log.info("[结果回收] 批次已终态，去重跳过 batchId={}", batchId);
            return;
        }
        // 仓库 / 令牌暂不可得按不可达留痕：修复配置后由轮询通道自愈，不提前终态化批次
        ApiGitRepo repo = head.getRepoId() == null ? null : gitRepoMapper.selectById(head.getRepoId());
        if (repo == null) {
            log.warn("[结果回收] 仓库配置已删除，推迟回收 batchId={}", batchId);
            return;
        }
        if (head.getPipelineIid() == null) {
            log.warn("[结果回收] 批次未关联流水线，推迟回收 batchId={}", batchId);
            return;
        }
        String token;
        try {
            token = decryptToken(repo);
        } catch (ServiceException e) {
            log.warn("[结果回收] 令牌不可解密，推迟回收 batchId={}：{}", batchId, e.getMessage());
            return;
        }

        byte[] artifacts;
        try {
            artifacts = gitLabApiClient.downloadPipelineArtifacts(
                    repo.getGitlabBaseUrl(), token, repo.getRepoPath(), head.getPipelineIid());
        } catch (ServiceException e) {
            if (channel == Channel.POLL) {
                // 流水线已终态仍取不到制品：整批失败降级并留痕（详设 4.6 对账与降级行）
                degrade(records, sourceStatus,
                        ErrorCodeConstants.API_GIT_ARTIFACT_DOWNLOAD_FAILED.code() + " " + e.getMessage());
            } else {
                log.info("[结果回收] 制品下载失败，推迟至轮询通道回收 batchId={}：{}", batchId, e.getMessage());
            }
            return;
        }
        if (artifacts == null) {
            if (channel == Channel.POLL) {
                degrade(records, sourceStatus, ErrorCodeConstants.API_GIT_ENVELOPE_INVALID.code()
                        + " 流水线无结果制品");
            } else {
                log.info("[结果回收] 制品尚未产出，推迟至轮询通道回收 batchId={}", batchId);
            }
            return;
        }

        // 归属过滤：只认批次快照内 sourceRef 的信封，外来文件不计入本批可用数
        Map<String, RyzeEnvelope> envelopes = parseEnvelopes(artifacts, batchId).entrySet().stream()
                .filter(e -> records.stream().anyMatch(r -> e.getKey().equals(r.getSourceRef())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));
        if (envelopes.isEmpty()) {
            degrade(records, sourceStatus, ErrorCodeConstants.API_GIT_ENVELOPE_INVALID.code()
                    + " 未回收到任何可用结果信封");
            return;
        }
        writeBack(records, envelopes);
        appendSuiteReport(records);
    }

    // ========== 信封解析 ==========

    /** 解析制品内结果目录信封（文件名 {@code 类全限定名#方法名.json}）；单文件失败只丢该信封 */
    private Map<String, RyzeEnvelope> parseEnvelopes(byte[] artifacts, String batchId) {
        Map<String, RyzeEnvelope> byRef = new LinkedHashMap<>();
        int files = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(artifacts))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory() || !name.contains(RESULT_DIR) || !name.endsWith(".json")) {
                    continue;
                }
                files++;
                try {
                    RyzeEnvelope envelope = RyzeEnvelopeParser.parse(
                            new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                    if (byRef.putIfAbsent(envelope.sourceRef(), envelope) != null) {
                        log.warn("[结果回收] 重复信封，保留先到者 ref={} batchId={}", envelope.sourceRef(), batchId);
                    }
                } catch (ServiceException e) {
                    log.warn("[结果回收] 信封解析失败，跳过文件 {} batchId={}：{}", name, batchId, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("[结果回收] 制品 zip 读取失败 batchId={}：{}", batchId, e.getMessage());
            return Map.of();
        }
        log.info("[结果回收] 制品信封 {} 个，可用 {} 个 batchId={}", files, byRef.size(), batchId);
        return byRef;
    }

    // ========== 逐条回写 ==========

    private void writeBack(List<ApiExecutionRecord> records, Map<String, RyzeEnvelope> envelopes) {
        Map<UUID, ApiScene> scenes = loadScenes(records);
        int actual = (int) records.stream()
                .filter(r -> r.getSourceRef() != null && envelopes.containsKey(r.getSourceRef())).count();
        Map<String, Object> summary = reconcileSummary(records.get(0), records.size(), actual);
        for (ApiExecutionRecord record : records) {
            if (TERMINAL_STATUSES.contains(record.getStatus())) {
                continue;
            }
            RyzeEnvelope envelope = record.getSourceRef() == null ? null
                    : envelopes.get(record.getSourceRef());
            if (envelope == null) {
                // 单条缺失 / 解析失败只置该记录 skipped 留痕，不影响其余用例回写；
                // 对账差值由批次 summary 的 errorFailed 承载（详设 4.6 对账行）
                executionRecordMapper.finishRecord(record.getId(), "skipped",
                        ErrorCodeConstants.API_GIT_ENVELOPE_INVALID.code()
                                + " " + ErrorCodeConstants.API_GIT_ENVELOPE_INVALID.msg(),
                        null, null, summary);
                continue;
            }
            writeOne(record, scenes.get(record.getSceneId()), envelope, summary);
        }
    }

    private void writeOne(ApiExecutionRecord record, ApiScene scene, RyzeEnvelope envelope,
            Map<String, Object> summary) {
        MappedResult root;
        try {
            root = RyzeResultMapper.map(RyzeEnvelopeParser.materialize(envelope),
                    properties.getDebug().getMaxResponseBodyChars());
        } catch (RuntimeException e) {
            root = null;
            log.warn("[结果回收] 信封物化失败，按解析失败计 recordId={}：{}", record.getId(), e.getMessage());
        }
        if (root == null) {
            executionRecordMapper.finishRecord(record.getId(), "skipped",
                    ErrorCodeConstants.API_GIT_ENVELOPE_INVALID.code()
                            + " " + ErrorCodeConstants.API_GIT_ENVELOPE_INVALID.msg(),
                    null, null, summary);
            return;
        }

        String sceneName = scene == null ? record.getSourceRef() : scene.getName();
        List<Map<String, Object>> steps = new ArrayList<>();
        if (root.sample()) {
            steps.add(stepEntry(root));
        } else {
            flatten(root.children(), steps);
        }

        int passed = 0;
        int failed = 0;
        int error = 0;
        int skipped = 0;
        for (Map<String, Object> step : steps) {
            switch (String.valueOf(step.get("status"))) {
                case "success" -> passed++;
                case "failed" -> failed++;
                case "skipped" -> skipped++;
                default -> error++;
            }
        }
        boolean anyError = error > 0 || "error".equals(root.status());
        String recordStatus = anyError ? "error"
                : (failed > 0 || "failed".equals(root.status()) ? "failed" : "success");
        // 报告状态口径（基础设施详细设计 2.1.4）：全过 success，引擎异常 failed，其余 partial
        String reportStatus = switch (recordStatus) {
            case "success" -> "success";
            case "error" -> "failed";
            default -> "partial";
        };
        Long elapsed = root.elapsedMs();
        Map<String, Object> reportSummary = new LinkedHashMap<>();
        reportSummary.put("total", steps.size());
        reportSummary.put("passed", passed);
        // 失败数含引擎异常（测试报告详细设计 2.3.1 场景汇总口径）
        reportSummary.put("failed", failed + error);
        reportSummary.put("skipped", skipped);
        reportSummary.put("durationMs", elapsed == null ? 0L : elapsed);

        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("sceneId", record.getSceneId().toString());
        dataset.put("sceneName", sceneName);
        dataset.put("status", reportStatus);
        dataset.put("execEngine", "gitlab");
        dataset.put("summary", reportSummary);
        // 批次冻结不绑定执行环境，CI 侧运行环境平台不可知
        dataset.put("environmentName", null);
        dataset.put("executedAt", UtcTime.toIsoFromSystemLocal(record.getExecutedAt()));
        dataset.put("steps", steps);
        dataset.put("preprocessors", ReportEntryVisitor.processorEntries(root.preprocessors()));
        dataset.put("postprocessors", ReportEntryVisitor.processorEntries(root.postprocessors()));

        ApiReport report = new ApiReport();
        report.setId(UUID.randomUUID());
        report.setProjectId(record.getProjectId());
        report.setExecutionRecordId(record.getId());
        report.setReportType("scene");
        report.setExternalId(record.getSceneId());
        report.setName(reportName(sceneName, record.getExecutedAt()));
        report.setExecutionMode(record.getExecutionMode());
        report.setSource(record.getSource() == null ? "scene" : record.getSource());
        report.setStatus(reportStatus);
        report.setSummary(reportSummary);
        report.setResult(dataset);
        report.setRyzeSnapshot(root.treeSnapshot());

        String errorMessage = "success".equals(recordStatus) ? null
                : truncate(lastFailure(steps, root));
        Integer durationMs = elapsed == null ? null : (int) Math.min(elapsed, Integer.MAX_VALUE);
        // 报告先插入、记录后占位：占位落空（另一通道先到）即回删报告，避免孤儿报告残留
        reportMapper.insert(report);
        int claimed = executionRecordMapper.finishRecord(record.getId(), recordStatus, errorMessage,
                report.getId(), durationMs, summary);
        if (claimed == 0) {
            reportMapper.deleteById(report.getId());
            log.info("[结果回收] 记录已被另一通道终态化，回删报告 reportId={}", report.getId());
        }
    }

    // ========== 整批降级（详设 4.6 / BR-1.34） ==========

    private void degrade(List<ApiExecutionRecord> records, String sourceStatus, String reason) {
        ApiExecutionRecord head = records.get(0);
        // 详设 4.6 状态映射：流水线 success / failed 均归 failed，canceled 归 cancelled
        String status = "canceled".equals(sourceStatus) || "cancelled".equals(sourceStatus)
                ? "cancelled" : "failed";
        String message = truncate(reason + (head.getPipelineUrl() == null
                ? "" : "；流水线：" + head.getPipelineUrl()));
        Map<String, Object> summary = reconcileSummary(head, records.size(), 0);
        if (!batchService.finishBatch(head.getBatchId(), status, message, summary)) {
            log.info("[结果回收] 批次已由另一通道终态化，降级写入被忽略 batchId={}", head.getBatchId());
        }
        // 降级同样入套件（占位数据集）：每次触发一份报告，不留空洞（WP-7.2）
        appendSuiteReport(records);
    }

    /**
     * 套件报告合并（WP-7.2，详设 4.5）：批次记录若有任务归属，将 CI 组场景数据集并入
     * 本触发的套件报告——平台组已首写则按 sceneId 幂等追加，纯 CI 任务（含触发失败、
     * 平台组崩溃无报告）由本入口首写；单场景执行（无任务归属）只有场景级报告，不适用。
     */
    private void appendSuiteReport(List<ApiExecutionRecord> records) {
        try {
            appender.append(records);
        } catch (RuntimeException e) {
            log.warn("[结果回收] 套件报告合并失败 batchId={}：{}", records.get(0).getBatchId(), e.getMessage());
        }
    }

    /**
     * 对账摘要：期望用例数 = 批次快照记录数，实际用例数 = 回收到可用信封的记录数，
     * 差值计 errorFailed（YAML 缺失等预期内差异不计执行失败，详设 4.6 对账行）。
     * 复制既有 summary 以保留 CI 回报计数等其他通道写入的键。
     */
    private static Map<String, Object> reconcileSummary(ApiExecutionRecord head,
            int expected, int actual) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (head.getSummary() != null) {
            summary.putAll(head.getSummary());
        }
        summary.put("expectedCases", expected);
        summary.put("actualCases", actual);
        summary.put("errorFailed", Math.max(0, expected - actual));
        if (head.getPipelineUrl() != null) {
            summary.put("pipelineUrl", head.getPipelineUrl());
        }
        return summary;
    }

    // ========== 映射工具 ==========

    /** 套件子节点无步骤语义，展开到采样叶（步骤元素即 ryze SampleResult，测试报告详细设计 2.3） */
    private static void flatten(List<MappedResult> nodes, List<Map<String, Object>> out) {
        if (nodes == null) {
            return;
        }
        for (MappedResult node : nodes) {
            if (node.sample()) {
                out.add(stepEntry(node));
            } else {
                flatten(node.children(), out);
            }
        }
    }

    private static Map<String, Object> stepEntry(MappedResult node) {
        Map<String, Object> entry = new LinkedHashMap<>();
        // CI 结果无平台步骤配置，无步骤 ID
        entry.put("stepId", null);
        entry.put("name", node.title());
        entry.put("type", "HTTP");
        entry.put("status", node.status());
        entry.put("durationMs", node.elapsedMs());
        entry.put("request", node.request());
        entry.put("response", node.response());
        entry.put("assertions", node.assertions());
        entry.put("extractors", node.extractors());
        entry.put("errorMessage", node.errorMessage());
        return entry;
    }

    /** 失败原因：取末个非成功步骤的错误消息，缺省回退根结果异常 */
    private static String lastFailure(List<Map<String, Object>> steps, MappedResult root) {
        for (int i = steps.size() - 1; i >= 0; i--) {
            Map<String, Object> step = steps.get(i);
            if (!"success".equals(step.get("status")) && step.get("errorMessage") != null) {
                return String.valueOf(step.get("errorMessage"));
            }
        }
        return root.errorMessage();
    }

    private Map<UUID, ApiScene> loadScenes(List<ApiExecutionRecord> records) {
        Set<UUID> sceneIds = records.stream()
                .map(ApiExecutionRecord::getSceneId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (sceneIds.isEmpty()) {
            return Map.of();
        }
        // 场景缺失（已删除）时回退 sourceRef 展示，不阻断回写
        return sceneMapper.selectBatchIds(sceneIds).stream()
                .collect(Collectors.toMap(ApiScene::getId, s -> s, (a, b) -> a));
    }

    /** 报告名称（执行时固化）：场景名 + 执行时间戳（测试报告详细设计 2.1.1） */
    private static String reportName(String sceneName, LocalDateTime executedAt) {
        LocalDateTime stamp = executedAt == null ? LocalDateTime.now() : executedAt;
        return sceneName + "-" + stamp.format(NAME_STAMP);
    }

    private static String truncate(String text) {
        if (text == null || text.length() <= MAX_ERROR_CHARS) {
            return text;
        }
        return text.substring(0, MAX_ERROR_CHARS);
    }

    /** 沿用已存令牌：解密失败（密钥轮换 / 密文损坏）按不可达留痕重试，禁止明文入日志 */
    private String decryptToken(ApiGitRepo repo) {
        String plain = secretKey == null ? null : SecretCryptoUtil.decrypt(secretKey, repo.getTokenCipher());
        if (plain == null || plain.isBlank()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_REPO_CONNECT_FAILED.code(),
                    "已存访问令牌无法解密");
        }
        return plain;
    }
}
