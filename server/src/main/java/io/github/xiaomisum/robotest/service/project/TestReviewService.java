package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.model.dto.request.TestReviewCasesUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.TestReviewCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.TestReviewRecordReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.*;

import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.UUID;

public interface TestReviewService {

    PageResult<TestReviewListRespDTO> getReviewPage(UUID projectId, String status, Integer pageNo, Integer pageSize);

    TestReviewDetailRespDTO createReview(UUID projectId, UUID userId, TestReviewCreateReqDTO reqDTO);

    TestReviewDetailRespDTO getReviewDetail(UUID reviewId);

    List<TestReviewSnapshotNodeRespDTO> getReviewSnapshotTree(UUID reviewId, UUID documentId);

    /**
     * 获取模块快照树（目录/文档层级），供详情页左侧文档切换
     *
     * @param reviewId 评审 ID
     * @return 模块快照树
     */
    List<SnapshotModuleTreeRespDTO> getReviewModuleTree(UUID reviewId);

    /**
     * 获取当前规划的用例（原始 documentId/caseId 维度），供调整弹窗回显
     *
     * @param reviewId 评审 ID
     * @return 规划用例列表
     */
    List<PlannedCasesRespDTO> getReviewPlannedCases(UUID reviewId);

    /**
     * 调整规划的用例：新增文档生成快照、移除文档删快照、保留文档补快照并重刷关联标记
     *
     * @param reviewId 评审 ID
     * @param reqDTO   新的用例选择
     */
    void updateReviewCases(UUID reviewId, TestReviewCasesUpdateReqDTO reqDTO);

    void submitReviewRecord(UUID reviewId, UUID userId, TestReviewRecordReqDTO reqDTO);

    List<TestReviewRecordRespDTO> getNodeReviewRecords(UUID reviewId, UUID nodeId);

    void completeReview(UUID reviewId, UUID userId);

    /**
     * 获取评审进度统计
     *
     * @param reviewId 评审 ID
     * @return 评审进度
     */
    TestReviewProgressRespDTO getReviewProgress(UUID reviewId);

    void syncReview(UUID reviewId, UUID userId);
}
