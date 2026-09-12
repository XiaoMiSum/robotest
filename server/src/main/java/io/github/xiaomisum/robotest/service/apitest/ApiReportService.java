package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiPublicReportRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiReportDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiReportPageItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiReportShareRespDTO;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 测试报告查询/分享/删除（测试报告详细设计、基础设施详细设计 3.4） */
public interface ApiReportService {

    PageResult<ApiReportPageItemRespDTO> page(UUID workspaceId, UUID projectId, UUID userId, PageParam pageParam,
            String status, String reportType, String executionMode, String keyword, LocalDateTime startDate, LocalDateTime endDate);

    ApiReportDetailRespDTO detail(UUID workspaceId, UUID projectId, UUID userId, UUID id);

    ApiReportShareRespDTO share(UUID workspaceId, UUID projectId, UUID userId, UUID id,
            Integer expiresInDays);

    /** 免登录访问：按 token 校验，过期/无效统一 7009（基础设施详细设计 4.2.2） */
    ApiPublicReportRespDTO publicAccess(UUID id, String token);

    void delete(UUID workspaceId, UUID projectId, UUID userId, UUID id);

    void batchDelete(UUID workspaceId, UUID projectId, UUID userId, List<UUID> ids);

}
