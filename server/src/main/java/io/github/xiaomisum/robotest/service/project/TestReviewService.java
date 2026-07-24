package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.model.dto.request.TestReviewCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.TestReviewRecordReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.*;

import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.UUID;

public interface TestReviewService {

    PageResult<TestReviewListRespDTO> getReviewPage(String projectId, String status, Integer pageNo, Integer pageSize);

    TestReviewDetailRespDTO createReview(String projectId, UUID userId, TestReviewCreateReqDTO reqDTO);

    TestReviewDetailRespDTO getReviewDetail(UUID reviewId);

    List<TestReviewSnapshotNodeRespDTO> getReviewSnapshotTree(UUID reviewId, UUID documentId);

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
