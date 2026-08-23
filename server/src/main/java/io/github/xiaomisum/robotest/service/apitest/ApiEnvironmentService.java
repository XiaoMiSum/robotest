package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentCopyReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentSortReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentIdRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentListItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentSetDefaultRespDTO;

import java.util.List;
import java.util.UUID;

/**
 * 接口测试环境管理（详细设计《环境管理详细设计说明书》3.1）。
 *
 * <p>权限口径：项目成员可查看，项目维护者可维护；上下文经 X-Active-Workspace /
 * X-Active-Project 请求头传递（C4）。</p>
 */
public interface ApiEnvironmentService {

    List<ApiEnvironmentListItemRespDTO> fetchEnvironments(UUID projectId, UUID workspaceId, UUID userId, String keyword);

    ApiEnvironmentDetailRespDTO getEnvironment(UUID projectId, UUID workspaceId, UUID userId, UUID id);

    ApiEnvironmentIdRespDTO createEnvironment(UUID projectId, UUID workspaceId, UUID userId,
            ApiEnvironmentSaveReqDTO reqDTO);

    void updateEnvironment(UUID projectId, UUID workspaceId, UUID userId, UUID id, ApiEnvironmentSaveReqDTO reqDTO);

    void deleteEnvironment(UUID projectId, UUID workspaceId, UUID userId, UUID id);

    ApiEnvironmentSetDefaultRespDTO setDefaultEnvironment(UUID projectId, UUID workspaceId, UUID userId, UUID id);

    /** 复制环境：含 HTTP 配置、变量与处理器；敏感变量值与数据源不复制（详细设计 3.1.11） */
    ApiEnvironmentIdRespDTO copyEnvironment(UUID projectId, UUID workspaceId, UUID userId, UUID id,
            ApiEnvironmentCopyReqDTO reqDTO);

    void sortEnvironment(UUID projectId, UUID workspaceId, UUID userId, UUID id, ApiEnvironmentSortReqDTO reqDTO);
}
