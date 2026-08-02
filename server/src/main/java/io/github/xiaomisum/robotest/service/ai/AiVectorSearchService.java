package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 向量检索与索引服务（《缺陷智能分析与向量检索详细设计说明书》2.2 / 4.1 / 4.2）。
 *
 * <p>职责：索引文本与 source_hash 计算、Embedding 调用（复用 {@link OpenAiCompatProvider}，
 * 10s 超时已内置）、向量表 UPSERT/删除、pgvector 余弦近邻检索。
 * 未配置 Embedding 时写链路与检索均安全空转（不抛异常、不写脏数据）。</p>
 */
public interface AiVectorSearchService {

    /** 缺陷语义查重命中（BugDedupHit） */
    record BugDedupHit(UUID bugId, String title, String status, UUID assigneeId, double similarity) {
    }

    /** 用例近邻命中（供遗漏分析/回归推荐等下游特性使用） */
    record CaseDedupHit(UUID nodeId, double similarity) {
    }

    /**
     * 缺陷语义查重（详细设计 4.2 检索 SQL + 应用层阈值过滤）；Embedding 未配置返回空列表
     */
    List<BugDedupHit> searchSimilarBugs(UUID projectId, String title, String reproSteps,
                                        UUID excludeBugId, int topK, double minSimilarity);

    /**
     * 用例近邻检索（同项目范围）；Embedding 未配置返回空列表
     */
    List<CaseDedupHit> searchSimilarCases(UUID projectId, String title, int topK, double minSimilarity);

    /**
     * 增量写入缺陷向量：source_hash 未变跳过；Embedding 调用失败仅 WARN 并返回 false（留待补偿，4.1）
     *
     * @return 是否实际完成向量写入
     */
    boolean indexBug(Bug bug);

    /**
     * 增量写入用例向量（节点所属文档与祖先/子节点上下文在内部解析）；失败语义同 {@link #indexBug}
     */
    boolean indexCase(TestCaseNode node);

    /** 缺陷逻辑删除时同步逻辑删除向量（4.1，与业务同事务） */
    void deleteBugIndex(UUID bugId);

    /** 用例节点删除时逻辑删除对应向量 */
    void deleteCaseIndex(UUID nodeId);

    /** 批量删除用例节点向量（文档删除/子树删除场景） */
    void deleteCaseIndexes(Collection<UUID> nodeIds);

    /**
     * 缺陷索引对象文本：title + "\n" + repro_steps 前 2000 字符（2.2）
     */
    String buildBugSourceText(Bug bug);

    /**
     * 缺陷表单查重请求的检索文本（与 {@link #buildBugSourceText} 同口径）
     */
    String buildBugQueryText(String title, String reproSteps);

    /**
     * 用例索引对象文本：文档名 + 祖先标题链 + case 标题 + 子节点（precondition/step/expected）标题，
     * 总长截断 2000 字符（2.2）
     */
    String buildCaseSourceText(String documentName, List<String> ancestorTitles,
                               TestCaseNode node, List<TestCaseNode> children);

    /**
     * 文档内全部 case 节点的索引文本（补偿/重建批量复用；祖先/子节点上下文在内部组装）
     */
    Map<UUID, String> buildCaseIndexTexts(String documentName, List<TestCaseNode> documentNodes);

    /**
     * source_hash = SHA-256(model + ":" + 索引对象文本)（2.2，模型或文本任一变化即过期）
     */
    String buildSourceHash(String model, String text);

    /**
     * 批量 Embedding 调用（供重建执行器按 50 条/批复用）；未配置或调用失败抛异常由调用方处理
     */
    List<float[]> embedBatch(List<String> texts);

    /**
     * 向量写入（重建执行器复用；vector 为空或与配置维度不符时静默跳过）
     */
    void upsertBug(UUID bugId, UUID projectId, float[] vector, String sourceHash);

    void upsertCase(UUID nodeId, UUID projectId, float[] vector, String sourceHash);

    /**
     * float[] → pgvector 文本字面量（float → double 精确转文本，保证 float4 回读一致）
     */
    String vectorToText(float[] vector);

    /**
     * 当前配置的向量维度；未配置返回 0
     */
    int configuredEmbeddingDimension();

    /**
     * 当前配置的 Embedding 模型名；未配置返回 null
     */
    String embeddingModel();
}
