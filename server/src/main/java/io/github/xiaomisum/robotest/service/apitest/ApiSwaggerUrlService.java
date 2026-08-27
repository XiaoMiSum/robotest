package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSwaggerUrlSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSwaggerUrlItemRespDTO;

import java.util.List;
import java.util.UUID;

/**
 * Swagger URL 配置管理（定时任务详细设计 3.1.9）
 */
public interface ApiSwaggerUrlService {

    List<ApiSwaggerUrlItemRespDTO> list(UUID workspaceId, UUID projectId, UUID userId, String name);

    UUID create(UUID workspaceId, UUID projectId, UUID userId, ApiSwaggerUrlSaveReqDTO reqDTO);

    void update(UUID workspaceId, UUID projectId, UUID userId, UUID id, ApiSwaggerUrlSaveReqDTO reqDTO);

    void delete(UUID workspaceId, UUID projectId, UUID userId, UUID id);
}
