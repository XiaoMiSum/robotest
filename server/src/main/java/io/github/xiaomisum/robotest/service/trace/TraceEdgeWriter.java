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
}
