package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.model.dto.request.review.TestReviewCasesUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.review.TestReviewCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.review.TestReviewRecordReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.review.TestReviewListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.review.TestReviewDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.review.TestReviewSnapshotNodeRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.review.TestReviewRecordRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.review.TestReviewProgressRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.SnapshotModuleTreeRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.PlannedCasesRespDTO;

import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.UUID;

public interface TestReviewService {

    PageResult<TestReviewListRespDTO> getReviewPage(UUID projectId, UUID userId, String status, String keyword,
            Integer pageNo, Integer pageSize);

    TestReviewDetailRespDTO createReview(UUID projectId, UUID userId, TestReviewCreateReqDTO reqDTO);

    TestReviewDetailRespDTO getReviewDetail(UUID reviewId, UUID userId);

    List<TestReviewSnapshotNodeRespDTO> getReviewSnapshotTree(UUID reviewId, UUID documentId, UUID userId);

    /**
     * 获取模块快照树（目录/文档层级），供详情页左侧文档切换
     *
     * @param reviewId 评审 ID
     * @param userId   当前用户 ID（用于项目归属校验）
     * @return 模块快照树
     */
    List<SnapshotModuleTreeRespDTO> getReviewModuleTree(UUID reviewId, UUID userId);

    /**
     * 获取当前规划的用例（原始 documentId/caseId 维度），供调整弹窗回显
     *
     * @param reviewId 评审 ID
     * @param userId   当前用户 ID（用于项目归属校验）
     * @return 规划用例列表
     */
    List<PlannedCasesRespDTO> getReviewPlannedCases(UUID reviewId, UUID userId);

    /**
     * 调整规划的用例：新增文档生成快照、移除文档删快照、保留文档补快照并重刷关联标记
     *
     * @param reviewId 评审 ID
     * @param userId   当前用户 ID（用于项目归属校验）
     * @param reqDTO   新的用例选择
     */
    void updateReviewCases(UUID reviewId, UUID userId, TestReviewCasesUpdateReqDTO reqDTO);

    void submitReviewRecord(UUID reviewId, UUID userId, TestReviewRecordReqDTO reqDTO);

    List<TestReviewRecordRespDTO> getNodeReviewRecords(UUID reviewId, UUID nodeId, UUID userId);

    void completeReview(UUID reviewId, UUID userId);

    /**
     * 删除评审及其快照、评审记录（仅发起人）
     *
     * @param reviewId 评审 ID
     * @param userId   操作人 ID
     */
    void deleteReview(UUID reviewId, UUID userId);

    /**
     * 获取评审进度统计
     *
     * @param reviewId 评审 ID
     * @param userId   当前用户 ID（用于项目归属校验）
     * @return 评审进度
     */
    TestReviewProgressRespDTO getReviewProgress(UUID reviewId, UUID userId);

    void syncReview(UUID reviewId, UUID userId);
}
