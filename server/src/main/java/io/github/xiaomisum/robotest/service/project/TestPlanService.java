package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.model.dto.request.TestPlanCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.TestPlanRecordReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.*;

import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;

public interface TestPlanService {

    PageResult<TestPlanListRespDTO> getPlanPage(String projectId, String status, Integer pageNo, Integer pageSize);

    TestPlanDetailRespDTO createPlan(String projectId, String userId, TestPlanCreateReqDTO reqDTO);

    TestPlanDetailRespDTO getPlanDetail(String planId);

    List<TestPlanSnapshotNodeRespDTO> getPlanSnapshotTree(String planId, String documentId);

    void submitExecutionRecord(String planId, String userId, TestPlanRecordReqDTO reqDTO);

    List<TestPlanExecutionRecordRespDTO> getNodeExecutionRecords(String planId, String nodeId);

    void syncPlan(String planId, String userId);

    /**
     * 启动计划（NEW → IN_PROGRESS）
     *
     * @param planId 计划 ID
     * @param userId 操作用户 ID
     */
    void startPlan(String planId, String userId);

    /**
     * 获取计划执行进度统计
     *
     * @param planId 计划 ID
     * @return 执行进度
     */
    TestPlanProgressRespDTO getPlanProgress(String planId);

    void closePlan(String planId, String userId);
}
