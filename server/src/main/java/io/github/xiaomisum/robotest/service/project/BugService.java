package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.model.dto.request.BugCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.BugUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugLogRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugStatisticsRespDTO;

import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;

public interface BugService {

    PageResult<BugListRespDTO> getBugPage(String projectId, String status, String severity,
                                     String priority, String assigneeId, String keyword,
                                     Integer pageNo, Integer pageSize);

    String createBug(String projectId, String userId, BugCreateReqDTO reqDTO);

    void updateBug(String bugId, String userId, BugUpdateReqDTO reqDTO);

    /**
     * 获取缺陷详情（含最近操作日志）
     *
     * @param bugId 缺陷 ID
     * @return 缺陷详情
     */
    BugDetailRespDTO getBugDetail(String bugId);

    /**
     * 变更缺陷状态（含状态机校验）
     *
     * @param bugId   缺陷 ID
     * @param userId  操作用户 ID
     * @param targetStatus 目标状态
     * @param comment 变更说明（重开/关闭时必填）
     */
    void changeBugStatus(String bugId, String userId, String targetStatus, String comment);

    /**
     * 指派缺陷处理人
     *
     * @param bugId      缺陷 ID
     * @param userId     操作用户 ID
     * @param assigneeId 新处理人用户 ID
     */
    void assignBug(String bugId, String userId, String assigneeId);

    /**
     * 统计项目缺陷概况
     *
     * @param projectId 项目 ID
     * @return 按状态/严重等级/优先级/处理人/报告人分组统计
     */
    BugStatisticsRespDTO getBugStatistics(String projectId);

    List<BugLogRespDTO> getBugLogs(String bugId);
}
