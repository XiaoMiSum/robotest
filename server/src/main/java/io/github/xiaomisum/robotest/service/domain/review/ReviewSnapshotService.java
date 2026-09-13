package io.github.xiaomisum.robotest.service.domain.review;

import io.github.xiaomisum.robotest.model.dto.request.review.TestReviewCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.PlannedCasesRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.review.TestReviewSnapshotNodeRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.SnapshotModuleTreeRespDTO;
import io.github.xiaomisum.robotest.model.entity.review.TestReviewNodeSnapshot;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 评审快照域服务：评审期间模块/节点快照的生成、调整、同步与读取。
 * 业务守卫（项目成员/发起人/状态机）由评审服务保留，本域只承载快照自身逻辑（C2）。
 */
public interface ReviewSnapshotService {

    /**
     * 按选中文档生成模块路径快照与用例节点快照（创建评审、新增文档共用）
     */
    void generateSnapshots(UUID reviewId, List<TestReviewCreateReqDTO.SelectedNode> selectedNodes);

    /**
     * 调整规划的用例：移除文档删快照并剪空目录、新增文档生成快照、保留文档补全并重刷关联标记
     *
     * @param projectId     项目 ID，用于校验选中文档归属
     * @param selectedNodes 新的用例选择
     */
    void updateCases(UUID reviewId, UUID projectId, List<TestReviewCreateReqDTO.SelectedNode> selectedNodes);

    /**
     * 同步模块/节点快照：名称排序跟随原始，已删除的原始模块/节点移除或标记
     */
    void syncSnapshots(UUID reviewId);

    /**
     * 级联删除评审的模块与节点快照（评审与记录删除由评审服务负责）
     */
    void deleteByReviewId(UUID reviewId);

    /**
     * 按 ID 查节点快照（提交评审记录时校验归属与类型）
     */
    TestReviewNodeSnapshot getNode(UUID snapshotNodeId);

    /**
     * 重置节点标记为待评审（pending 等价语义）
     */
    void resetMarkAsPending(UUID snapshotNodeId, UUID reviewerId, LocalDateTime reviewedAt);

    /**
     * 更新节点评审标记（仅携带标记字段，避免整行覆盖并发评审结果）
     */
    void applyMark(UUID snapshotNodeId, UUID reviewerId, String mark, LocalDateTime reviewedAt);

    /**
     * 查询文档下快照树：以关联用例为根保留祖先与后代，剪除孤立节点
     */
    List<TestReviewSnapshotNodeRespDTO> getSnapshotTree(UUID reviewId, UUID documentId);

    /**
     * 模块快照树（目录/文档层级），供详情页左侧文档切换
     */
    List<SnapshotModuleTreeRespDTO> getModuleTree(UUID reviewId);

    /**
     * 规划用例列表（原始 documentId/caseId 维度），供调整弹窗回显
     */
    List<PlannedCasesRespDTO> getPlannedCases(UUID reviewId);

    /**
     * 批量查关联用例快照（列表页进度/通过率统计，避免 N+1）
     */
    List<TestReviewNodeSnapshot> listAssociatedByReviewIds(Collection<UUID> reviewIds, String type);

    /**
     * 按评审查关联用例快照（进度统计）
     */
    List<TestReviewNodeSnapshot> listAssociatedByReviewId(UUID reviewId, String type);
}