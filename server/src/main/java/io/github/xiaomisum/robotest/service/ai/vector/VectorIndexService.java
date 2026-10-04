package io.github.xiaomisum.robotest.service.ai.vector;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.f4b6a3.uuid.UuidCreator;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.ai.AiEmbeddingConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiVectorIndex;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.ai.AiVectorIndexMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiEmbeddingClient;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 向量索引服务（详设 4.4）：写侧保存链路同步重嵌（用户裁决——故障上抛牵连保存），
 * 未配置 / 未启用 / 全量重建中一律静默跳过（4.5 降级口径：保存不因向量底座缺失而失败）。
 * 重嵌发生在落库之前，失败时索引与业务数据同步回滚，不留半套分块。
 */
@Component
public class VectorIndexService {

    public static final String TYPE_REQUIREMENT = "requirement";
    public static final String TYPE_TESTCASE = "testcase";
    public static final String TYPE_BUG = "bug";

    /** 详设 3.6.1 scope.entityTypes 白名单（全量重建的类型序） */
    public static final List<String> ALL_TYPES = List.of(TYPE_REQUIREMENT, TYPE_TESTCASE, TYPE_BUG);

    @Resource
    private AiEmbeddingGate gate;
    @Resource
    private AiEmbeddingClient embeddingClient;
    @Resource
    private AiVectorIndexMapper indexMapper;
    @Resource
    private RequirementMapper requirementMapper;
    @Resource
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private BugMapper bugMapper;

    // ---------- 写侧入口（保存链路） ----------

    public void upsertRequirement(UUID requirementId, UUID userId) {
        AiEmbeddingConfig config = gate.writableConfig();
        if (config == null) {
            return;
        }
        Requirement row = requirementMapper.selectById(requirementId);
        if (row == null || Constants.RequirementStatus.ARCHIVED.equals(row.getStatus())) {
            remove(TYPE_REQUIREMENT, requirementId);
            return;
        }
        apply(config, TYPE_REQUIREMENT, requirementId, row.getProjectId(), userId,
                join(row.getTitle(), row.getDescription()));
    }

    /** 归档 / 逻辑删除移出索引（4.4），不依赖向量配置是否就绪 */
    public void removeRequirement(UUID requirementId) {
        remove(TYPE_REQUIREMENT, requirementId);
    }

    public void upsertTestCase(UUID documentId, UUID userId) {
        AiEmbeddingConfig config = gate.writableConfig();
        if (config == null) {
            return;
        }
        TestCaseDocument document = testCaseDocumentMapper.selectById(documentId);
        if (document == null) {
            remove(TYPE_TESTCASE, documentId);
            return;
        }
        apply(config, TYPE_TESTCASE, documentId, document.getProjectId(), userId,
                contentFor(TYPE_TESTCASE, documentId));
    }

    public void removeTestCase(UUID documentId) {
        remove(TYPE_TESTCASE, documentId);
    }

    public void upsertBug(UUID bugId, UUID userId) {
        AiEmbeddingConfig config = gate.writableConfig();
        if (config == null) {
            return;
        }
        Bug row = bugMapper.selectById(bugId);
        if (row == null) {
            remove(TYPE_BUG, bugId);
            return;
        }
        apply(config, TYPE_BUG, bugId, row.getProjectId(), userId, join(row.getTitle(), row.getReproSteps()));
    }

    // ---------- 供重建 handler 复用 ----------

    /**
     * 实体可检索正文（单一事实源，写侧与全量重建共用同一装配口径）。
     *
     * @return 实体不存在返回 null（调用方按移除处理）
     */
    public String contentFor(String type, UUID entityId) {
        return switch (type) {
            case TYPE_REQUIREMENT -> {
                Requirement row = requirementMapper.selectById(entityId);
                yield row == null ? null : join(row.getTitle(), row.getDescription());
            }
            case TYPE_TESTCASE -> testCaseContent(entityId);
            case TYPE_BUG -> {
                Bug row = bugMapper.selectById(entityId);
                yield row == null ? null : join(row.getTitle(), row.getReproSteps());
            }
            default -> null;
        };
    }

    /** 分块 + 批量向量化，返回待写行（不落库）；空内容返回空结果。外部调用失败原样上抛 1000018117。 */
    public EmbedRows embedRows(AiEmbeddingConfig config, String type, UUID entityId, UUID projectId,
            UUID userId, String content) {
        List<String> chunks = VectorChunker.chunk(content);
        if (chunks.isEmpty()) {
            return new EmbedRows(List.of(), 0);
        }
        AiEmbeddingClient.EmbeddingsReply reply =
                embeddingClient.embedBatch(config, chunks, userId, null, projectId);
        LocalDateTime now = LocalDateTime.now();
        String version = versionOf(config);
        List<AiVectorIndex> rows = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            AiVectorIndex row = new AiVectorIndex();
            // id 走框架默认策略（时间有序 UUID）：insertBatch 不经过 MP insertFill
            row.setId(UuidCreator.getTimeOrderedEpoch());
            row.setProjectId(projectId);
            row.setEntityType(type);
            row.setEntityId(entityId);
            row.setChunkIndex(i);
            row.setContent(chunks.get(i));
            row.setEmbedding(toVectorText(reply.vectors().get(i)));
            row.setEmbeddingVersion(version);
            row.setIndexedAt(now);
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            rows.add(row);
        }
        return new EmbedRows(rows, reply.tokens());
    }

    /** 某实体的索引整体替换：先逻辑删旧分块再插新分块（唯一索引按活跃分块约束，顺序不可反） */
    public void replace(String type, UUID entityId, List<AiVectorIndex> rows) {
        deleteByEntity(type, entityId);
        if (!rows.isEmpty()) {
            indexMapper.insertBatch(rows);
        }
    }

    private void deleteByEntity(String type, UUID entityId) {
        indexMapper.delete(Wrappers.lambdaQuery(AiVectorIndex.class)
                .eq(AiVectorIndex::getEntityType, type)
                .eq(AiVectorIndex::getEntityId, entityId));
    }

    /** 待写分块行与本次向量化消耗的 token（重建任务记账用） */
    public record EmbedRows(List<AiVectorIndex> rows, int tokens) {
    }

    // ---------- 私有 ----------

    private void apply(AiEmbeddingConfig config, String type, UUID entityId, UUID projectId, UUID userId,
            String content) {
        // 先向量化后落库：嵌入失败时业务保存随异常整体回滚，索引不产生半套分块
        EmbedRows embedRows = embedRows(config, type, entityId, projectId, userId, content);
        replace(type, entityId, embedRows.rows());
    }

    private void remove(String type, UUID entityId) {
        deleteByEntity(type, entityId);
    }

    /** 用例正文 = 文档名 + 节点标题（前置 / 步骤 / 预期在当前模型里均为节点标题承载） */
    private String testCaseContent(UUID documentId) {
        TestCaseDocument document = testCaseDocumentMapper.selectById(documentId);
        if (document == null) {
            return null;
        }
        StringBuilder content = new StringBuilder(document.getName() == null ? "" : document.getName());
        List<TestCaseNode> nodes = testCaseNodeMapper.selectList(new LambdaQueryWrapperX<TestCaseNode>()
                .eq(TestCaseNode::getDocumentId, documentId)
                .orderByAsc(TestCaseNode::getSortOrder));
        for (TestCaseNode node : nodes) {
            if (node.getTitle() != null && !node.getTitle().isBlank()
                    && !node.getTitle().equals(document.getName())) {
                content.append('\n').append(node.getTitle());
            }
        }
        return content.toString();
    }

    private static String join(String title, String body) {
        if (title == null || title.isBlank()) {
            return body == null ? "" : body;
        }
        if (body == null || body.isBlank()) {
            return title;
        }
        return title + "\n" + body;
    }

    /** 嵌入版本标识：模型 + 维度 + 算子任一变化即为新版本，供重建前后归因 */
    private static String versionOf(AiEmbeddingConfig config) {
        return config.getEmbeddingModel() + ":" + config.getDimensions() + ":" + config.getOperator();
    }

    /** pgvector 文本表示（[0.1,0.2]），写入时经 CAST(… AS vector) 转列类型（读侧查询向量化同用） */
    static String toVectorText(List<Double> vector) {
        StringBuilder text = new StringBuilder(vector.size() * 8 + 2).append('[');
        for (int i = 0; i < vector.size(); i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(vector.get(i));
        }
        return text.append(']').toString();
    }
}
