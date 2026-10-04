package io.github.xiaomisum.robotest.service.domain.requirement;

import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementPageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementChangeLogRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementSplitRecordRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementSplitSubmitRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.trace.TraceChainRespDTO;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.UUID;

/**
 * 需求条目服务（详设 3.x / 4.x）：列表筛选、部分更新、状态机与拆解记录查询。
 * 所有查询强制 project_id 隔离，越权返回 404（4.6）。
 */
public interface RequirementService {

    PageResult<RequirementListRespDTO> page(RequirementPageReqDTO pageReq, UUID projectId);

    RequirementDetailRespDTO getDetail(UUID id, UUID projectId);

    RequirementDetailRespDTO create(UUID projectId, UUID userId, RequirementCreateReqDTO reqDTO);

    /** 部分更新（C11）：仅更新传入字段；confirmed 态改题/描述/模块自动转 changed 并触发影响分析（4.4） */
    RequirementDetailRespDTO update(UUID id, UUID projectId, UUID userId, RequirementUpdateReqDTO reqDTO);

    /** 确认：仅 draft / changed 可确认（3.6） */
    RequirementDetailRespDTO confirm(UUID id, UUID projectId, UUID userId);

    /** 归档：非归档态均可归档，归档后只读（3.7） */
    RequirementDetailRespDTO archive(UUID id, UUID projectId, UUID userId);

    /** 取消归档：仅归档态可取消，一律回到 draft（3.7） */
    RequirementDetailRespDTO unarchive(UUID id, UUID projectId, UUID userId);

    /** 条目内 AI 拆分（3.9）：任务与拆解记录同事务提交，返回任务入口 */
    RequirementSplitSubmitRespDTO split(UUID id, UUID projectId, UUID userId);

    PageResult<RequirementChangeLogRespDTO> getChangeLogs(UUID id, UUID projectId, PageParam pageParam);

    /** 按原条目反查拆解记录（3.11） */
    PageResult<RequirementSplitRecordRespDTO> getSplitLogs(UUID id, UUID projectId, PageParam pageParam);

    /** 项目内拆解记录列表（3.11） */
    PageResult<RequirementSplitRecordRespDTO> getSplitRecords(UUID projectId, String status, String sourceType,
            PageParam pageParam);

    /**
     * 追溯委托（3.12）：以需求为链路起点读取追溯链；矩阵服务调用失败返回 1000018014，
     * 不降级为空链路；空链路按 nodes = [] 正常返回。
     */
    TraceChainRespDTO getTrace(UUID id, UUID projectId, String direction);
}
