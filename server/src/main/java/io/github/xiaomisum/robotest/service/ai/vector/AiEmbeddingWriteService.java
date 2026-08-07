package io.github.xiaomisum.robotest.service.ai.vector;

import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;

import java.util.Collection;
import java.util.UUID;

/**
 * 向量增量写入与补偿（《缺陷智能分析与向量检索详细设计说明书》4.1）。
 *
 * <p>业务事件（缺陷变更/用例节点变更）经事务提交后调用本服务；
 * 存在进行中的 {@code embedding_rebuild} 任务时直接跳过（重建互斥），
 * 空窗期变更由补偿任务追平。补偿任务多实例经 Redis 锁保证单实例执行。</p>
 */
public interface AiEmbeddingWriteService {

    /** 缺陷创建/标题/重现步骤变更后触发（事务提交后异步调用） */
    void handleBugChanged(Bug bug);

    /** 缺陷逻辑删除时同步删除向量（与业务同事务调用） */
    void handleBugDeleted(UUID bugId);

    /** 用例节点变更后触发（标题或子节点变更、含新增） */
    void handleCaseChanged(TestCaseNode node);

    /** 单个用例节点删除 */
    void handleCaseDeleted(UUID nodeId);

    /** 批量用例节点删除（文档/子树删除场景） */
    void handleCasesDeleted(Collection<UUID> nodeIds);

    /**
     * 补偿扫描（每 10 分钟）：逐项目补齐无向量或 hash 过期的未关闭缺陷与 case 节点，
     * 每轮上限 200 条；重建互斥 + Redis 分布式锁。
     */
    void compensate();
}
