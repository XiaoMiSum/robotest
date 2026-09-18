package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.model.dto.response.bug.BugDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugLogRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugStatisticsRespDTO;

import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.UUID;

/**
 * 缺陷读模型端口（05 §3.1.3 CQRS 落位）：列表/详情/日志/统计等只读查询与命令面分离。
 * 命令面见 {@link BugService}。
 */
public interface BugQueryService {

    PageResult<BugListRespDTO> getBugPage(UUID projectId, UUID userId, String status, String severity,
                                     String priority, String bugType, UUID assigneeId,
                                     UUID reporterId, UUID resolvedBy, UUID closedBy, String keyword,
                                     Integer pageNo, Integer pageSize);

    /**
     * 获取缺陷详情（含最近操作日志）
     *
     * @param bugId  缺陷 ID
     * @param userId 当前用户 ID（用于项目归属校验）
     * @return 缺陷详情
     */
    BugDetailRespDTO getBugDetail(UUID bugId, UUID userId);

    /**
     * 统计项目缺陷概况
     *
     * @param projectId 项目 ID
     * @param userId    当前用户 ID（用于项目归属校验）
     * @return 按状态/严重等级/优先级/处理人/报告人分组统计
     */
    BugStatisticsRespDTO getBugStatistics(UUID projectId, UUID userId);

    List<BugLogRespDTO> getBugLogs(UUID bugId, UUID userId);
}