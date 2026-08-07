package io.github.xiaomisum.robotest.service.ai.bug;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.ai.AiAnalysisTask;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.repository.ai.AiAnalysisTaskMapper;
import io.github.xiaomisum.robotest.repository.ai.BugEmbeddingMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.service.ai.gateway.AiConfigService;
import io.github.xiaomisum.robotest.service.ai.gateway.AiGatewayService;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.AiCallContext;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ChatCallOptions;
import io.github.xiaomisum.robotest.service.ai.support.AiOutputValidator;
import io.github.xiaomisum.robotest.service.ai.support.AiTaskProgressSupport;
import io.github.xiaomisum.robotest.service.ai.support.AiTextUtils;
import io.github.xiaomisum.robotest.service.ai.support.AiVectorMath;
import io.github.xiaomisum.robotest.service.ai.task.AiTaskHandler;
import io.github.xiaomisum.robotest.service.ai.vector.AiVectorSearchService;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.util.JsonUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * bug_clustering 任务处理器（US-AI-010，详细设计 4.3）：
 * 取数（无向量现场补建）→ 贪心增量聚类（阈值 + 归一化中心，确定性）→
 * 前 maxLabeledClusters 簇 LLM 归纳（每簇一次、批间心跳 + 协作式取消）→ 落库 2.3 快照。
 * 只读洞察，不修改缺陷数据。
 */
@Slf4j
@Component
public class AiBugClusteringTaskHandler implements AiTaskHandler {

    public static final String TYPE = Constants.AiTaskType.BUG_CLUSTERING;

    /** 代表样本数（4.3 步骤 3：离中心最近 5 条） */
    static final int REPRESENTATIVE_SAMPLE_LIMIT = 5;
    /** 单样本描述截断预算（token，控制单簇归纳输入规模） */
    static final int SAMPLE_DESC_TOKEN_BUDGET = 600;
    /** 归纳主题名上限（与任务指令一致） */
    static final int LABEL_MAX_LEN = 100;
    /** 未指定模块的聚合名称（2.3） */
    static final String UNSPECIFIED_MODULE_NAME = "未指定模块";

    private static final String LABEL_TASK_INSTRUCTION = """
            请归纳给定缺陷样本反映的共同主题。输出单个 JSON 对象，仅含两个字段：\
            label（≤100 字符的主题名称，概括该组缺陷的共性）与 rootCause（一句话根因推断；\
            证据不足时使用「疑似」措辞，给出最可能范围）。""";

    @Resource
    private AiAnalysisTaskMapper aiTaskMapper;
    @Resource
    private BugMapper bugMapper;
    @Resource
    private BugEmbeddingMapper bugEmbeddingMapper;
    @Resource
    private TestCaseModuleMapper testCaseModuleMapper;
    @Resource
    private AiVectorSearchService vectorSearchService;
    @Resource
    private AiConfigService aiConfigService;
    @Resource
    private AiGatewayService aiGatewayService;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Map<String, Object> execute(AiAnalysisTask task) {
        UUID projectId = task.getProjectId();
        List<Bug> bugs = bugMapper.findOpenBugsForClustering(projectId);
        // 1. 取数（10%）：无向量现场补建，补建仍缺失的缺陷归入 unclustered
        Map<UUID, float[]> vectors = loadVectors(projectId);
        for (Bug bug : bugs) {
            if (!vectors.containsKey(bug.getId())) {
                try {
                    vectorSearchService.indexBug(bug);
                } catch (Exception e) {
                    log.warn("[AI] 聚类补建向量失败 bugId={}: {}", bug.getId(), e.getMessage());
                }
            }
        }
        vectors = loadVectors(projectId);
        List<GreedyCluster> clusters = new ArrayList<>();
        List<UUID> unclustered = new ArrayList<>();
        if (heartbeat(task, 10, bugs, clusters, unclustered) == 0) {
            return buildSnapshot(bugs, clusters, unclustered);
        }

        // 2. 聚类（40%）：贪心增量，单缺陷簇归入 unclustered；按簇大小降序保证归纳优先级确定性
        double threshold = aiConfigService.getNumberSetting("clustering.similarityThreshold");
        GreedyResult greedy = greedyClustering(bugs, vectors, threshold);
        clusters = greedy.clusters();
        unclustered = greedy.unclustered();
        clusters.sort(Comparator.comparingInt((GreedyCluster c) -> c.bugIds.size()).reversed());
        if (heartbeat(task, 40, bugs, clusters, unclustered) == 0) {
            return buildSnapshot(bugs, clusters, unclustered);
        }

        // 3. 归纳（40% → 90%）：仅前 maxLabeledClusters 簇，每簇一次 LLM 调用、批间心跳 + 取消检查
        int maxLabeled = aiConfigService.getIntSetting("clustering.maxLabeledClusters");
        int labeledCount = Math.min(clusters.size(), maxLabeled);
        for (int i = 0; i < labeledCount; i++) {
            int progress = 40 + (int) Math.round(50.0 * (i + 1) / labeledCount);
            if (heartbeat(task, progress, bugs, clusters, unclustered) == 0) {
                return buildSnapshot(bugs, clusters, unclustered);
            }
            try {
                labelCluster(task, clusters.get(i), bugs, vectors);
            } catch (Exception e) {
                // LLM 失败/限流 → 保留「未命名主题 N」，不阻断整体任务（4.3 步骤 3）
                log.warn("[AI] 聚类归纳失败，保留未命名主题: {}", e.getMessage());
            }
        }

        // 4. 落库（100% 由任务框架置位）：组装 2.3 快照
        return buildSnapshot(bugs, clusters, unclustered);
    }

    /** 单簇 LLM 归纳（代表样本 = 离中心最近 5 条标题 + 截断描述，4.3 步骤 3） */
    private void labelCluster(AiAnalysisTask task, GreedyCluster cluster, List<Bug> bugs, Map<UUID, float[]> vectors) {
        AiCallContext context = new AiCallContext(task.getCreatedBy(), task.getWorkspaceId(), task.getProjectId());
        ClusterLabelOut out = aiGatewayService.completeStructured(
                context, AiFunctionType.BUG_CLUSTERING, LABEL_TASK_INSTRUCTION,
                buildSampleData(cluster, bugs, vectors), ChatCallOptions.json(),
                ClusterLabelOut.class, this::assertLabel);
        cluster.label = out.getLabel();
        cluster.rootCause = out.getRootCause();
    }

    /** 贪心增量聚类：与已有簇中心余弦相似度 ≥ 阈值则并入并更新中心（归一化向量均值再归一化），否则新建簇 */
    private GreedyResult greedyClustering(List<Bug> bugs, Map<UUID, float[]> vectors, double threshold) {
        List<GreedyCluster> clusters = new ArrayList<>();
        List<UUID> unclustered = new ArrayList<>();
        for (Bug bug : bugs) {
            float[] vec = vectors.get(bug.getId());
            float[] norm = vec == null ? null : AiVectorMath.normalize(vec);
            if (norm == null) {
                unclustered.add(bug.getId());
                continue;
            }
            GreedyCluster best = null;
            double bestSim = threshold;
            for (GreedyCluster c : clusters) {
                double sim = AiVectorMath.dot(c.center, norm);
                if (sim >= bestSim) {
                    bestSim = sim;
                    best = c;
                }
            }
            if (best == null) {
                GreedyCluster created = new GreedyCluster();
                created.bugIds.add(bug.getId());
                created.sum = norm.clone();
                created.center = norm.clone();
                clusters.add(created);
            } else {
                best.bugIds.add(bug.getId());
                AiVectorMath.addInPlace(best.sum, norm);
                best.center = AiVectorMath.normalize(best.sum);
            }
        }
        // 单缺陷簇无归纳价值，归入 unclustered（4.3 步骤 2）
        List<GreedyCluster> multi = new ArrayList<>();
        for (GreedyCluster c : clusters) {
            if (c.bugIds.size() == 1) {
                unclustered.add(c.bugIds.get(0));
            } else {
                multi.add(c);
            }
        }
        return new GreedyResult(multi, unclustered);
    }

    /** 代表样本：簇内按与中心余弦相似度降序取前 N 条，样本文本 = 标题 + 截断描述 */
    private String buildSampleData(GreedyCluster cluster, List<Bug> bugs, Map<UUID, float[]> vectors) {
        Map<UUID, Bug> bugById = bugs.stream().collect(Collectors.toMap(Bug::getId, b -> b, (a, b) -> a));
        List<UUID> samples = cluster.bugIds.stream()
                .sorted(Comparator.comparingDouble((UUID id) -> -AiVectorMath.dot(AiVectorMath.normalize(vectors.get(id)), cluster.center))
                        .thenComparing(UUID::toString))
                .limit(REPRESENTATIVE_SAMPLE_LIMIT)
                .toList();
        StringBuilder sb = new StringBuilder("【缺陷样本】\n");
        int index = 1;
        for (UUID id : samples) {
            Bug bug = bugById.get(id);
            sb.append(index++).append(". 标题：").append(bug.getTitle()).append('\n');
            if (StringUtils.hasText(bug.getReproSteps())) {
                sb.append("   描述：")
                        .append(AiTextUtils.truncateToTokenBudget(bug.getReproSteps(), SAMPLE_DESC_TOKEN_BUDGET))
                        .append('\n');
            }
        }
        return sb.toString();
    }

    /** 结构断言：label 非空 ≤100 字符、rootCause 非空（schema 与任务指令一致） */
    private void assertLabel(ClusterLabelOut out) {
        if (!StringUtils.hasText(out.getLabel()) || out.getLabel().length() > LABEL_MAX_LEN) {
            throw new AiOutputValidator.OutputValidationException(
                    "label 不能为空且不能超过 " + LABEL_MAX_LEN + " 字符");
        }
        if (!StringUtils.hasText(out.getRootCause())) {
            throw new AiOutputValidator.OutputValidationException("rootCause 不能为空");
        }
    }

    /** 进度心跳：写部分快照（未命名主题占位），影响行数为 0 表示任务被取消/置失败，调用方应立即中止 */
    private int heartbeat(AiAnalysisTask task, int progress, List<Bug> bugs,
            List<GreedyCluster> clusters, List<UUID> unclustered) {
        return AiTaskProgressSupport.heartbeat(aiTaskMapper, task.getId(), progress,
                JsonUtils.toJsonString(buildSnapshot(bugs, clusters, unclustered)));
    }

    /** 组装 2.3 快照：未命名簇按输出位置编号「未命名主题 N」，unclustered 升序保证确定性 */
    private Map<String, Object> buildSnapshot(List<Bug> bugs, List<GreedyCluster> clusters, List<UUID> unclustered) {
        Map<UUID, Bug> bugById = bugs.stream().collect(Collectors.toMap(Bug::getId, b -> b, (a, b) -> a));
        Map<UUID, String> moduleNames = resolveModuleNames(clusters, bugById);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("generatedAt", Instant.now().toString());
        snapshot.put("bugCount", bugs.size());
        List<Map<String, Object>> clusterOut = new ArrayList<>();
        for (int i = 0; i < clusters.size(); i++) {
            GreedyCluster cluster = clusters.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", StringUtils.hasText(cluster.label) ? cluster.label : "未命名主题 " + (i + 1));
            item.put("rootCause", cluster.rootCause);
            item.put("bugIds", cluster.bugIds.stream().map(UUID::toString).toList());
            item.put("severityDist", buildSeverityDist(cluster.bugIds, bugById));
            item.put("moduleDist", buildModuleDist(cluster.bugIds, bugById, moduleNames));
            clusterOut.add(item);
        }
        snapshot.put("clusters", clusterOut);
        snapshot.put("unclustered", unclustered.stream().map(UUID::toString).sorted().toList());
        return snapshot;
    }

    /** 簇涉及模块名批量解析（仅一次查询；软删除模块回退「未知模块」） */
    private Map<UUID, String> resolveModuleNames(List<GreedyCluster> clusters, Map<UUID, Bug> bugById) {
        Set<UUID> moduleIds = new HashSet<>();
        for (GreedyCluster c : clusters) {
            for (UUID id : c.bugIds) {
                Bug bug = bugById.get(id);
                if (bug != null && bug.getModuleId() != null) {
                    moduleIds.add(bug.getModuleId());
                }
            }
        }
        Map<UUID, String> names = new HashMap<>();
        if (!moduleIds.isEmpty()) {
            for (TestCaseModule module : testCaseModuleMapper.selectBatchIds(new ArrayList<>(moduleIds))) {
                names.put(module.getId(), module.getName());
            }
        }
        return names;
    }

    /** severityDist：四键零初始化保证 schema 稳定，未知等级忽略不新增桶 */
    private Map<String, Integer> buildSeverityDist(List<UUID> bugIds, Map<UUID, Bug> bugById) {
        Map<String, Integer> dist = new LinkedHashMap<>();
        dist.put(Constants.BugSeverity.FATAL, 0);
        dist.put(Constants.BugSeverity.SERIOUS, 0);
        dist.put(Constants.BugSeverity.GENERAL, 0);
        dist.put(Constants.BugSeverity.MINOR, 0);
        for (UUID id : bugIds) {
            Bug bug = bugById.get(id);
            if (bug != null && bug.getSeverity() != null) {
                dist.merge(bug.getSeverity(), 1, Integer::sum);
            }
        }
        return dist;
    }

    /** moduleDist：按模块聚合，moduleId 为空的聚合为「未指定模块」；按数量降序保证展示稳定 */
    private List<Map<String, Object>> buildModuleDist(List<UUID> bugIds, Map<UUID, Bug> bugById,
            Map<UUID, String> moduleNames) {
        Map<UUID, Integer> counts = new HashMap<>();
        Map<UUID, String> names = new HashMap<>();
        int unspecified = 0;
        for (UUID id : bugIds) {
            Bug bug = bugById.get(id);
            UUID moduleId = bug == null ? null : bug.getModuleId();
            if (moduleId == null) {
                unspecified++;
            } else {
                counts.merge(moduleId, 1, Integer::sum);
                names.putIfAbsent(moduleId, moduleNames.getOrDefault(moduleId, "未知模块"));
            }
        }
        List<Map<String, Object>> dist = new ArrayList<>();
        counts.forEach((moduleId, count) -> dist.add(moduleEntry(moduleId.toString(), names.get(moduleId), count)));
        if (unspecified > 0) {
            dist.add(moduleEntry(null, UNSPECIFIED_MODULE_NAME, unspecified));
        }
        dist.sort(Comparator.<Map<String, Object>>comparingInt(m -> (int) m.get("count")).reversed()
                .thenComparing(m -> String.valueOf(m.get("moduleName"))));
        return dist;
    }

    private Map<String, Object> moduleEntry(String moduleId, String moduleName, int count) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("moduleId", moduleId);
        entry.put("moduleName", moduleName);
        entry.put("count", count);
        return entry;
    }

    /** 向量文本 → float[]（vectorToText 的反向解析，格式为 "[v1,v2,…]"） */
    private Map<UUID, float[]> loadVectors(UUID projectId) {
        Map<UUID, float[]> vectors = new HashMap<>();
        for (var embedding : bugEmbeddingMapper.findEmbeddingsByProjectId(projectId)) {
            float[] parsed = AiVectorMath.parseVector(embedding.getEmbedding());
            if (parsed != null) {
                vectors.put(embedding.getBugId(), parsed);
            }
        }
        return vectors;
    }

    /** 聚类中的簇：sum 为成员归一化向量累加和，center = normalize(sum)（增量更新 O(1)） */
    private static class GreedyCluster {

        private final List<UUID> bugIds = new ArrayList<>();
        private float[] sum;
        private float[] center;
        private String label;
        private String rootCause;
    }

    private record GreedyResult(List<GreedyCluster> clusters, List<UUID> unclustered) {
    }

    /** LLM 结构化输出：簇主题归纳（label/rootCause 合法性经 Bean Validation + 自定义断言双重兜底） */
    @Data
    public static class ClusterLabelOut {

        @NotBlank(message = "label 不能为空")
        @Size(max = 100, message = "label 不能超过 100 字符")
        private String label;

        @NotBlank(message = "rootCause 不能为空")
        private String rootCause;
    }
}
