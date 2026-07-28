package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.model.dto.request.TestPlanCasesUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.TestPlanCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.TestPlanRecordReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.*;

import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.UUID;

public interface TestPlanService {

    PageResult<TestPlanListRespDTO> getPlanPage(UUID projectId, String status, String keyword, Integer pageNo,
            Integer pageSize);

    TestPlanDetailRespDTO createPlan(UUID projectId, UUID userId, TestPlanCreateReqDTO reqDTO);

    TestPlanDetailRespDTO getPlanDetail(UUID planId);

    List<TestPlanSnapshotNodeRespDTO> getPlanSnapshotTree(UUID planId, UUID documentId);

    /**
     * 获取模块快照树（目录/文档层级），供详情页左侧文档切换
     *
     * @param planId 计划 ID
     * @return 模块快照树
     */
    List<SnapshotModuleTreeRespDTO> getPlanModuleTree(UUID planId);

    /**
     * 获取当前规划的用例（原始 documentId/caseId 维度），供调整弹窗回显
     *
     * @param planId 计划 ID
     * @return 规划用例列表
     */
    List<PlannedCasesRespDTO> getPlanPlannedCases(UUID planId);

    /**
     * 调整规划的用例：新增文档生成快照、移除文档删快照、保留文档补快照并重刷关联标记
     *
     * @param planId 计划 ID
     * @param reqDTO 新的用例选择
     */
    void updatePlanCases(UUID planId, TestPlanCasesUpdateReqDTO reqDTO);

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
