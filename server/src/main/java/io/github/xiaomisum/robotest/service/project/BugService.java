package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.model.dto.request.BugCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.BugStatusChangeReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.BugUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugLogRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugStatisticsRespDTO;

import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.UUID;

public interface BugService {

    PageResult<BugListRespDTO> getBugPage(UUID projectId, String status, String severity,
                                     String priority, String bugType, UUID assigneeId, String keyword,
                                     Integer pageNo, Integer pageSize);

    String createBug(UUID projectId, UUID userId, BugCreateReqDTO reqDTO);

    void updateBug(UUID bugId, UUID userId, BugUpdateReqDTO reqDTO);

    /**
     * 获取缺陷详情（含最近操作日志）
     *
     * @param bugId 缺陷 ID
     * @return 缺陷详情
     */
    BugDetailRespDTO getBugDetail(UUID bugId);

    /**
     * 变更缺陷状态（三态状态机：active → resolved → closed，重开回 active）
     *
     * @param bugId  缺陷 ID
     * @param userId 操作用户 ID
     * @param reqDTO 目标状态、说明、解决方案及重复缺陷指向
     */
    void changeBugStatus(UUID bugId, UUID userId, BugStatusChangeReqDTO reqDTO);

    /**
     * 确认缺陷（仅激活状态且未确认时可执行）
     *
     * @param bugId  缺陷 ID
     * @param userId 操作用户 ID
     */
    void confirmBug(UUID bugId, UUID userId);

    /**
     * 指派缺陷处理人
     *
     * @param bugId      缺陷 ID
     * @param userId     操作用户 ID
     * @param assigneeId 新处理人用户 ID
     */
    void assignBug(UUID bugId, UUID userId, UUID assigneeId);

    /**
     * 统计项目缺陷概况
     *
     * @param projectId 项目 ID
     * @return 按状态/严重等级/优先级/处理人/报告人分组统计
     */
    BugStatisticsRespDTO getBugStatistics(UUID projectId);

    List<BugLogRespDTO> getBugLogs(UUID bugId);
}
