package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.ai.BugEmbedding;
import io.github.xiaomisum.robotest.model.entity.ai.CaseEmbedding;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.ai.BugEmbeddingMapper;
import io.github.xiaomisum.robotest.repository.ai.CaseEmbeddingMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 向量索引与检索实现（详细设计 2.2 / 4.1 / 4.2）。
 *
 * <p>写链路失败均降级为 WARN 并返回 false，由 4.1 补偿任务兜底；Embedding 未配置时安全空转。</p>
 */
@Slf4j
@Service
public class AiVectorSearchServiceImpl implements AiVectorSearchService {

    private static final int SOURCE_TEXT_MAX = 2000;

    @Resource
    private OpenAiCompatProvider openAiCompatProvider;
    @Resource
    private AiConfigService aiConfigService;
    @Resource
    private BugEmbeddingMapper bugEmbeddingMapper;
    @Resource
    private CaseEmbeddingMapper caseEmbeddingMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private TestCaseModuleMapper testCaseModuleMapper;

    @Override
    public List<BugDedupHit> searchSimilarBugs(UUID projectId, String title, String reproSteps,
                                               UUID excludeBugId, int topK, double minSimilarity) {
        ResolvedAiConfig config = resolvedConfig();
        if (config == null || projectId == null) {
            return List.of();
        }
        float[] query = embedSingle(config, buildBugQueryText(title, reproSteps));
        if (query == null) {
            return List.of();
        }
        List<BugEmbeddingMapper.SearchRow> rows = bugEmbeddingMapper.searchTopK(
                projectId.toString(), vectorToText(query),
                excludeBugId != null ? excludeBugId.toString() : null, topK);
        return rows.stream()
                .filter(row -> row.getSimilarity() != null && row.getSimilarity() >= minSimilarity)
                .map(row -> new BugDedupHit(parseUuid(row.getBugId()), row.getTitle(), row.getStatus(),
                        parseUuid(row.getAssigneeId()), row.getSimilarity()))
                .toList();
    }

    @Override
    public List<CaseDedupHit> searchSimilarCases(UUID projectId, String title, int topK, double minSimilarity) {
        ResolvedAiConfig config = resolvedConfig();
        if (config == null || projectId == null) {
            return List.of();
        }
        float[] query = embedSingle(config, title);
        if (query == null) {
            return List.of();
        }
        List<CaseEmbeddingMapper.SearchRow> rows = caseEmbeddingMapper.searchTopK(
                projectId.toString(), vectorToText(query), topK);
        return rows.stream()
                .filter(row -> row.getSimilarity() != null && row.getSimilarity() >= minSimilarity)
                .map(row -> new CaseDedupHit(parseUuid(row.getNodeId()), row.getSimilarity()))
                .toList();
    }

    @Override
    public boolean indexBug(Bug bug) {
        ResolvedAiConfig config = resolvedConfig();
        if (config == null || bug == null || bug.getProjectId() == null) {
            return false;
        }
        String model = config.embeddingModel();
        String hash = buildSourceHash(model, buildBugSourceText(bug));
        BugEmbedding existing = bugEmbeddingMapper.findByBugId(bug.getId());
        if (existing != null && model.equals(existing.getModel()) && hash.equals(existing.getSourceHash())) {
            return false;
        }
        float[] vector = embedSingle(config, buildBugSourceText(bug));
        if (vector == null) {
            return false;
        }
        upsertBug(bug.getId(), bug.getProjectId(), vector, hash);
        return true;
    }

    @Override
    public boolean indexCase(TestCaseNode node) {
        ResolvedAiConfig config = resolvedConfig();
        if (config == null || node == null || node.getDocumentId() == null) {
            return false;
        }
        TestCaseModule module = testCaseModuleMapper.selectById(node.getDocumentId());
        if (module == null || module.getProjectId() == null) {
            log.warn("[AI] 用例向量写入跳过：节点 {} 所属文档不存在", node.getId());
            return false;
        }
        List<TestCaseNode> nodes = testCaseNodeMapper.listByDocumentId(node.getDocumentId());
        String text = buildCaseIndexTexts(module.getName(), nodes).get(node.getId());
        if (text == null) {
            return false;
        }
        String model = config.embeddingModel();
        String hash = buildSourceHash(model, text);
        CaseEmbedding existing = caseEmbeddingMapper.findByNodeId(node.getId());
        if (existing != null && model.equals(existing.getModel()) && hash.equals(existing.getSourceHash())) {
            return false;
        }
        float[] vector = embedSingle(config, text);
        if (vector == null) {
            return false;
        }
        upsertCase(node.getId(), module.getProjectId(), vector, hash);
        return true;
    }

    @Override
    public void deleteBugIndex(UUID bugId) {
        bugEmbeddingMapper.logicalDeleteByBugId(bugId);
    }

    @Override
    public void deleteCaseIndex(UUID nodeId) {
        caseEmbeddingMapper.logicalDeleteByNodeId(nodeId);
    }

    @Override
    public void deleteCaseIndexes(Collection<UUID> nodeIds) {
        if (nodeIds != null && !nodeIds.isEmpty()) {
            caseEmbeddingMapper.logicalDeleteByNodeIds(nodeIds);
        }
    }

    @Override
    public String buildBugSourceText(Bug bug) {
        return buildBugQueryText(bug.getTitle(), bug.getReproSteps());
    }

    @Override
    public String buildBugQueryText(String title, String reproSteps) {
        return (title == null ? "" : title) + "\n" + truncate(reproSteps, SOURCE_TEXT_MAX);
    }

    @Override
    public String buildCaseSourceText(String documentName, List<String> ancestorTitles,
                                      TestCaseNode node, List<TestCaseNode> children) {
        StringBuilder sb = new StringBuilder();
        sb.append("文档：").append(documentName == null ? "" : documentName).append('\n');
        if (ancestorTitles != null && !ancestorTitles.isEmpty()) {
            sb.append("路径：").append(String.join(" > ", ancestorTitles)).append('\n');
        }
        sb.append("用例标题：").append(node.getTitle() == null ? "" : node.getTitle()).append('\n');
        if (children != null && !children.isEmpty()) {
            Map<String, List<String>> byType = children.stream().collect(Collectors.groupingBy(
                    TestCaseNode::getType, LinkedHashMap::new,
                    Collectors.mapping(c -> c.getTitle() == null ? "" : c.getTitle(), Collectors.toList())));
            appendTitledChildren(sb, "前置条件", byType.get(Constants.NodeType.PRECONDITION));
            appendTitledChildren(sb, "步骤", byType.get(Constants.NodeType.STEP));
            appendTitledChildren(sb, "预期结果", byType.get(Constants.NodeType.EXPECTED));
        }
        return sb.length() > SOURCE_TEXT_MAX ? sb.substring(0, SOURCE_TEXT_MAX) : sb.toString();
    }

    @Override
    public Map<UUID, String> buildCaseIndexTexts(String documentName, List<TestCaseNode> documentNodes) {
        Map<UUID, TestCaseNode> nodeById = documentNodes.stream()
                .collect(Collectors.toMap(TestCaseNode::getId, n -> n, (a, b) -> a));
        Map<UUID, String> result = new HashMap<>();
        for (TestCaseNode node : documentNodes) {
            if (!Constants.NodeType.CASE.equals(node.getType())) {
                continue;
            }
            result.put(node.getId(), buildCaseSourceText(documentName,
                    ancestorTitles(node, nodeById), node, childNodes(node, documentNodes)));
        }
        return result;
    }

    @Override
    public String buildSourceHash(String model, String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((model + ":" + text).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        ResolvedAiConfig config = resolvedConfig();
        if (config == null) {
            return List.of();
        }
        return openAiCompatProvider.embed(config, texts).vectors();
    }

    @Override
    public void upsertBug(UUID bugId, UUID projectId, float[] vector, String sourceHash) {
        if (!isValidVector(vector)) {
            return;
        }
        ResolvedAiConfig config = resolvedConfig();
        if (config == null) {
            return;
        }
        BugEmbedding entity = new BugEmbedding();
        entity.setId(UUID.randomUUID());
        entity.setBugId(bugId);
        entity.setProjectId(projectId);
        entity.setEmbedding(vectorToText(vector));
        entity.setSourceHash(sourceHash);
        entity.setModel(config.embeddingModel());
        bugEmbeddingMapper.upsert(entity);
    }

    @Override
    public void upsertCase(UUID nodeId, UUID projectId, float[] vector, String sourceHash) {
        if (!isValidVector(vector)) {
            return;
        }
        ResolvedAiConfig config = resolvedConfig();
        if (config == null) {
            return;
        }
        CaseEmbedding entity = new CaseEmbedding();
        entity.setId(UUID.randomUUID());
        entity.setNodeId(nodeId);
        entity.setProjectId(projectId);
        entity.setEmbedding(vectorToText(vector));
        entity.setSourceHash(sourceHash);
        entity.setModel(config.embeddingModel());
        caseEmbeddingMapper.upsert(entity);
    }

    @Override
    public String vectorToText(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(Double.toString(vector[i]));
        }
        return sb.append(']').toString();
    }

    @Override
    public int configuredEmbeddingDimension() {
        ResolvedAiConfig config = resolvedConfig();
        return config != null && config.embeddingConfigured() ? config.embeddingDimension() : 0;
    }

    @Override
    public String embeddingModel() {
        ResolvedAiConfig config = resolvedConfig();
        return config != null && config.embeddingConfigured() ? config.embeddingModel() : null;
    }

    private ResolvedAiConfig resolvedConfig() {
        return aiConfigService.getResolvedConfig();
    }

    /**
     * 单文本 Embedding：配置缺失或调用失败返回 null（不抛异常，由调用方按跳过处理）
     */
    private float[] embedSingle(ResolvedAiConfig config, String text) {
        if (config == null || text == null) {
            return null;
        }
        try {
            List<float[]> vectors = openAiCompatProvider.embed(config, List.of(text)).vectors();
            return vectors.isEmpty() ? null : vectors.get(0);
        } catch (Exception e) {
            log.warn("[AI] Embedding 调用失败（留待补偿）: {}", e.getMessage());
            return null;
        }
    }

    private boolean isValidVector(float[] vector) {
        if (vector == null || vector.length == 0) {
            return false;
        }
        int expected = configuredEmbeddingDimension();
        return expected == 0 || vector.length == expected;
    }

    private List<TestCaseNode> childNodes(TestCaseNode node, List<TestCaseNode> allNodes) {
        return allNodes.stream()
                .filter(c -> node.getId().equals(c.getParentId()))
                .toList();
    }

    /** 祖先标题链（父 → 根，不含自身，供路径拼接） */
    private List<String> ancestorTitles(TestCaseNode node, Map<UUID, TestCaseNode> nodeById) {
        List<String> titles = new ArrayList<>();
        Set<UUID> visited = new java.util.HashSet<>();
        TestCaseNode cursor = node.getParentId() != null ? nodeById.get(node.getParentId()) : null;
        while (cursor != null && visited.add(cursor.getId())) {
            titles.addFirst(cursor.getTitle() == null ? "" : cursor.getTitle());
            cursor = cursor.getParentId() != null ? nodeById.get(cursor.getParentId()) : null;
        }
        return titles;
    }

    private void appendTitledChildren(StringBuilder sb, String label, List<String> titles) {
        sb.append(label).append('：');
        if (titles == null || titles.isEmpty()) {
            sb.append("无\n");
        } else {
            sb.append(String.join("；", titles)).append('\n');
        }
    }

    private String truncate(String text, int max) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.length() > max ? text.substring(0, max) : text;
    }

    private UUID parseUuid(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
