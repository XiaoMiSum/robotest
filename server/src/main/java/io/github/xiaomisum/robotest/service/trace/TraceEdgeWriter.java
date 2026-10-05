package io.github.xiaomisum.robotest.service.trace;

import java.util.Collection;
import java.util.UUID;

/**
 * 追溯边写入口（追溯矩阵详设 4.1 建边时机）：供评审 / 计划的快照事务调用，
 * 保证「圈选集合 ⇄ 快照引用边」一致；派生边的批量建边随生成链采纳（WP-5.1）另行接入。
 */
public interface TraceEdgeWriter {

    /**
     * 快照圈选对账：按当前圈选用例集合补齐 case_snapshot 边并清理已移出集合的边；
     * 保留边的 target_version 不刷新（版本锚定在首次圈选时点，后续不一致由 stale 承载）。
     *
     * @param caseIds   当前圈选的用例节点 ID 集合
     * @param operatorId 圈选操作人，落 confirmed_by
     */
    void syncCaseSnapshotEdges(UUID projectId, String targetType, UUID targetId, Collection<UUID> caseIds,
            UUID operatorId);

    /** 目标（评审 / 计划）删除时清理其全部 case_snapshot 边 */
    void removeCaseSnapshotEdges(UUID projectId, String targetType, UUID targetId);

    /**
     * 文档关联需求对账：以 requirement → mindmap_document 的 derivation 边反向承载文档关联
     * （目标节点类型取 mindmap_document，不改动已确认的类型组合），按当前勾选集合补齐并清理多余边；
     * 重新勾选已断开的关联时恢复该边（detached 行占用唯一索引，不得重复插入）。
     *
     * @param requirementIds 当前勾选的需求 ID 集合，空集合表示清空关联
     * @param operatorId     保存操作人，落 confirmed_by
     */
    void syncDocumentRequirementEdges(UUID projectId, UUID docId, Collection<UUID> requirementIds, UUID operatorId);

    /**
     * AI 派生批量建边（生成链详设 3.5 采纳事务）：一次采纳为目标写 requirement → 目标 的 derivation 边，
     * status = ai_created、established_by = ai，等人工在矩阵转确认；target_version 锚定新实体版本
     * （无版本列的实体为 null）。同对已有任何有效边（含 detached，AI 不得重建）时跳过，
     * 并发重复由唯一约束先到者为准，不中断采纳事务。
     *
     * @param requirementIds 来源需求 ID 集合（产物 sourceRefs），空集合不写
     * @param targetType     目标节点类型（module / mindmap_document / test_case）
     * @param targetVersion  新实体版本（"v1" 形态；实体无版本列传 null）
     */
    void writeAiDerivationEdges(UUID projectId, Collection<UUID> requirementIds, String targetType,
            UUID targetId, String targetVersion);
}
