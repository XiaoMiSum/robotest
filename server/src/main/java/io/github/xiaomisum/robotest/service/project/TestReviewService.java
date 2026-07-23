package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.model.dto.request.TestReviewCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.TestReviewRecordReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.*;

import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;

public interface TestReviewService {

    PageResult<TestReviewListRespDTO> getReviewPage(String projectId, String status, Integer pageNo, Integer pageSize);

    TestReviewDetailRespDTO createReview(String projectId, String userId, TestReviewCreateReqDTO reqDTO);

    TestReviewDetailRespDTO getReviewDetail(String reviewId);

    List<TestReviewSnapshotNodeRespDTO> getReviewSnapshotTree(String reviewId, String documentId);

    void submitReviewRecord(String reviewId, String userId, TestReviewRecordReqDTO reqDTO);

    List<TestReviewRecordRespDTO> getNodeReviewRecords(String reviewId, String nodeId);

    void completeReview(String reviewId, String userId);

    /**
     * 获取评审进度统计
     *
     * @param reviewId 评审 ID
     * @return 评审进度
     */
    TestReviewProgressRespDTO getReviewProgress(String reviewId);

    void syncReview(String reviewId, String userId);
}
