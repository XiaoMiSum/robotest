package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.model.dto.request.TestPlanCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.TestPlanRecordReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.*;

import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.UUID;

public interface TestPlanService {

    PageResult<TestPlanListRespDTO> getPlanPage(String projectId, String status, Integer pageNo, Integer pageSize);

    TestPlanDetailRespDTO createPlan(String projectId, UUID userId, TestPlanCreateReqDTO reqDTO);

    TestPlanDetailRespDTO getPlanDetail(UUID planId);

    List<TestPlanSnapshotNodeRespDTO> getPlanSnapshotTree(UUID planId, UUID documentId);

    void submitExecutionRecord(UUID planId, UUID userId, TestPlanRecordReqDTO reqDTO);

    List<TestPlanExecutionRecordRespDTO> getNodeExecutionRecords(UUID planId, UUID nodeId);

    void syncPlan(UUID planId, UUID userId);

    /**
     * 启动计划（NEW → IN_PROGRESS）
     *
     * @param planId 计划 ID
     * @param userId 操作用户 ID
     */
    void startPlan(UUID planId, UUID userId);

    /**
     * 获取计划执行进度统计
     *
     * @param planId 计划 ID
     * @return 执行进度
     */
    TestPlanProgressRespDTO getPlanProgress(UUID planId);

    void closePlan(UUID planId, UUID userId);
}
