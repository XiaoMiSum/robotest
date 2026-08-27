package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiScheduleSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiScheduleToggleReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiScheduleValidateCronReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiScheduleCreatedRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiScheduleExecuteNowRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiScheduleExecutionItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSchedulePageItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiScheduleValidateCronRespDTO;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.UUID;

/**
 * 定时任务管理（定时任务详细设计 3.1）
 */
public interface ApiScheduleService {

    PageResult<ApiSchedulePageItemRespDTO> page(UUID workspaceId, UUID projectId, UUID userId,
            String taskType, PageParam pageParam);

    ApiScheduleCreatedRespDTO create(UUID workspaceId, UUID projectId, UUID userId, ApiScheduleSaveReqDTO reqDTO);

    void update(UUID workspaceId, UUID projectId, UUID userId, UUID id, ApiScheduleSaveReqDTO reqDTO);

    void toggle(UUID workspaceId, UUID projectId, UUID userId, UUID id, ApiScheduleToggleReqDTO reqDTO);

    void delete(UUID workspaceId, UUID projectId, UUID userId, UUID id);

    /** 手动触发一次，不受 Cron 影响；上一次未结束抛 7603 */
    ApiScheduleExecuteNowRespDTO executeNow(UUID workspaceId, UUID projectId, UUID userId, UUID id);

    PageResult<ApiScheduleExecutionItemRespDTO> executions(UUID workspaceId, UUID projectId, UUID userId,
            UUID taskId, PageParam pageParam);

    ApiScheduleValidateCronRespDTO validateCron(ApiScheduleValidateCronReqDTO reqDTO);

}
