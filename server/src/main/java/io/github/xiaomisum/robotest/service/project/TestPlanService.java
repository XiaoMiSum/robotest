package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.model.dto.request.plan.TestPlanCasesUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.plan.TestPlanCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.plan.TestPlanRecordReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanSnapshotNodeRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanExecutionRecordRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanProgressRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.SnapshotModuleTreeRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.PlannedCasesRespDTO;

import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.UUID;

public interface TestPlanService {

    PageResult<TestPlanListRespDTO> getPlanPage(UUID projectId, UUID userId, String status, String keyword,
            Integer pageNo, Integer pageSize);

    TestPlanDetailRespDTO createPlan(UUID projectId, UUID userId, TestPlanCreateReqDTO reqDTO);

    TestPlanDetailRespDTO getPlanDetail(UUID planId, UUID userId);

    List<TestPlanSnapshotNodeRespDTO> getPlanSnapshotTree(UUID planId, UUID documentId, UUID userId);

    /**
     * 获取模块快照树（目录/文档层级），供详情页左侧文档切换
     *
     * @param planId 计划 ID
     * @param userId 当前用户 ID（用于项目归属校验）
     * @return 模块快照树
     */
    List<SnapshotModuleTreeRespDTO> getPlanModuleTree(UUID planId, UUID userId);

    /**
     * 获取当前规划的用例（原始 documentId/caseId 维度），供调整弹窗回显
     *
     * @param planId 计划 ID
     * @param userId 当前用户 ID（用于项目归属校验）
     * @return 规划用例列表
     */
    List<PlannedCasesRespDTO> getPlanPlannedCases(UUID planId, UUID userId);

    /**
     * 调整规划的用例：新增文档生成快照、移除文档删快照、保留文档补快照并重刷关联标记
     *
     * @param planId 计划 ID
     * @param userId 当前用户 ID（用于项目归属校验）
     * @param reqDTO 新的用例选择
     */
    void updatePlanCases(UUID planId, UUID userId, TestPlanCasesUpdateReqDTO reqDTO);

    void submitExecutionRecord(UUID planId, UUID userId, TestPlanRecordReqDTO reqDTO);

    List<TestPlanExecutionRecordRespDTO> getNodeExecutionRecords(UUID planId, UUID nodeId, UUID userId);

    void syncPlan(UUID planId, UUID userId);

    /**
     * 获取计划执行进度统计
     *
     * @param planId 计划 ID
     * @param userId 当前用户 ID（用于项目归属校验）
     * @return 执行进度
     */
    TestPlanProgressRespDTO getPlanProgress(UUID planId, UUID userId);

    void closePlan(UUID planId, UUID userId);

    /**
     * 完成计划（仅负责人）
     *
     * @param planId 计划 ID
     * @param userId 操作人 ID
     */
    void completePlan(UUID planId, UUID userId);

    /**
     * 删除计划及其快照、执行记录（仅负责人）
     *
     * @param planId 计划 ID
     * @param userId 操作人 ID
     */
    void deletePlan(UUID planId, UUID userId);
}
