package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentCopyReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentDataSourceSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentHttpConfigSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentProcessorSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentSortReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentVariableBatchReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentVariableCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentVariableImportReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiDataSourceTestRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvImportResultRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentIdRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentListItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentProcessorRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentSetDefaultRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentVariableRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiHttpTestRespDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * 接口测试环境管理（详细设计《环境管理详细设计说明书》3.1–3.3）。
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

    // ========== 处理器子资源（3.2） ==========

    List<ApiEnvironmentProcessorRespDTO> listProcessors(UUID projectId, UUID workspaceId, UUID userId,
            UUID id, String processorType);

    ApiEnvironmentProcessorRespDTO createProcessor(UUID projectId, UUID workspaceId, UUID userId,
            UUID id, ApiEnvironmentProcessorSaveReqDTO reqDTO);

    ApiEnvironmentProcessorRespDTO updateProcessor(UUID projectId, UUID workspaceId, UUID userId,
            UUID id, UUID procId, ApiEnvironmentProcessorSaveReqDTO reqDTO);

    void deleteProcessor(UUID projectId, UUID workspaceId, UUID userId, UUID id, UUID procId);

    // ========== HTTP 配置子资源（3.1.12） ==========

    ApiEnvironmentDetailRespDTO.HttpConfig createHttpConfig(UUID projectId, UUID workspaceId, UUID userId,
            UUID id, ApiEnvironmentHttpConfigSaveReqDTO reqDTO);

    ApiEnvironmentDetailRespDTO.HttpConfig updateHttpConfig(UUID projectId, UUID workspaceId, UUID userId,
            UUID id, UUID httpConfigId, ApiEnvironmentHttpConfigSaveReqDTO reqDTO);

    void deleteHttpConfig(UUID projectId, UUID workspaceId, UUID userId, UUID id, UUID httpConfigId);

    // ========== 数据源子资源（3.1.12） ==========

    ApiEnvironmentDetailRespDTO.DataSource createDataSource(UUID projectId, UUID workspaceId, UUID userId,
            UUID id, ApiEnvironmentDataSourceSaveReqDTO reqDTO);

    ApiEnvironmentDetailRespDTO.DataSource updateDataSource(UUID projectId, UUID workspaceId, UUID userId,
            UUID id, UUID dataSourceId, ApiEnvironmentDataSourceSaveReqDTO reqDTO);

    void deleteDataSource(UUID projectId, UUID workspaceId, UUID userId, UUID id, UUID dataSourceId);

    // ========== 变量子资源（3.3） ==========

    /** 全量替换环境变量（3.3.1） */
    List<ApiEnvironmentVariableRespDTO> batchReplaceVariables(UUID projectId, UUID workspaceId, UUID userId,
            UUID id, ApiEnvironmentVariableBatchReqDTO reqDTO);

    /** 从执行结果添加单条变量，重名报「变量已存在」（3.3.2） */
    ApiEnvironmentVariableRespDTO addVariableFromResult(UUID projectId, UUID workspaceId, UUID userId,
            UUID id, ApiEnvironmentVariableCreateReqDTO reqDTO);

    ApiEnvImportResultRespDTO importVariables(UUID projectId, UUID workspaceId, UUID userId,
            UUID id, ApiEnvironmentVariableImportReqDTO reqDTO);

    /** 导出变量（3.3.4） */
    List<ApiEnvironmentVariableRespDTO> exportVariables(UUID projectId, UUID workspaceId, UUID userId, UUID id);

    // ========== 连接测试（3.1.7 / 3.1.8） ==========

    ApiDataSourceTestRespDTO testDataSource(UUID projectId, UUID workspaceId, UUID userId,
            UUID id, UUID dataSourceId);

    ApiHttpTestRespDTO testHttpConfig(UUID projectId, UUID workspaceId, UUID userId,
            UUID id, UUID httpConfigId);

    // ========== 环境导入导出（3.1.9 / 3.1.10） ==========

    /** 导出环境聚合配置 JSON：敏感字段脱敏（3.1.9） */
    ApiEnvironmentDetailRespDTO exportEnvironment(UUID projectId, UUID workspaceId, UUID userId, UUID id);

    /** 导入环境 JSON 文件；重名按 overwrite 覆盖或跳过（3.1.10） */
    ApiEnvImportResultRespDTO importEnvironment(UUID projectId, UUID workspaceId, UUID userId,
            MultipartFile file, boolean overwrite);
}
