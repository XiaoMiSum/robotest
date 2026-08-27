package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSwaggerUrlSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSwaggerUrlItemRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTask;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiSwaggerUrl;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSwaggerUrlMapper;
import io.github.xiaomisum.robotest.service.apitest.imports.ImportSourceFetcher;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.util.List;
import java.util.UUID;

import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_SWAGGER_URL_NOT_FOUND;
import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_SWAGGER_URL_TASK_BOUND;

/**
 * Swagger URL 配置管理实现（定时任务详细设计 3.1.9）：
 * 新增/更新时经 ImportSourceFetcher 校验可达性与 SSRF，删除前检查任务绑定
 */
@Service
public class ApiSwaggerUrlServiceImpl implements ApiSwaggerUrlService {

    private final ImportSourceFetcher sourceFetcher = new ImportSourceFetcher();

    @Resource
    private ApiSwaggerUrlMapper swaggerUrlMapper;
    @Resource
    private ApiScheduledTaskMapper taskMapper;
    @Resource
    private ProjectAccessGuard projectAccessGuard;

    @Override
    public List<ApiSwaggerUrlItemRespDTO> list(UUID workspaceId, UUID projectId, UUID userId, String name) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        return swaggerUrlMapper.selectListByProject(projectId, name).stream()
                .map(config -> ApiSwaggerUrlItemRespDTO.builder()
                        .id(config.getId())
                        .name(config.getName())
                        .url(config.getUrl())
                        .format(config.getFormat())
                        .lastImportStatus(config.getLastImportStatus())
                        .lastImportAt(config.getLastImportAt())
                        .createdAt(config.getCreatedAt())
                        .build())
                .toList();
    }

    @Override
    public UUID create(UUID workspaceId, UUID projectId, UUID userId, ApiSwaggerUrlSaveReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireReachable(reqDTO.getUrl());
        ApiSwaggerUrl config = new ApiSwaggerUrl();
        config.setId(UUID.randomUUID());
        config.setProjectId(projectId);
        config.setName(reqDTO.getName().trim());
        config.setUrl(reqDTO.getUrl().trim());
        config.setFormat(reqDTO.getFormat());
        swaggerUrlMapper.insert(config);
        return config.getId();
    }

    @Override
    public void update(UUID workspaceId, UUID projectId, UUID userId, UUID id, ApiSwaggerUrlSaveReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireConfig(projectId, id);
        requireReachable(reqDTO.getUrl());
        // 部分更新原则（C9）：仅更新调用方传入的字段
        swaggerUrlMapper.update(null, new LambdaUpdateWrapperX<ApiSwaggerUrl>()
                .eq(ApiSwaggerUrl::getId, id)
                .set(ApiSwaggerUrl::getName, reqDTO.getName().trim())
                .set(ApiSwaggerUrl::getUrl, reqDTO.getUrl().trim())
                .set(ApiSwaggerUrl::getFormat, reqDTO.getFormat()));
    }

    @Override
    public void delete(UUID workspaceId, UUID projectId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireConfig(projectId, id);
        Long boundCount = taskMapper.selectCount(new LambdaQueryWrapperX<ApiScheduledTask>()
                .eq(ApiScheduledTask::getTaskType, "import_swagger")
                .eq(ApiScheduledTask::getBoundObjectId, id));
        if (boundCount != null && boundCount > 0) {
            throw ServiceExceptionUtil.get(API_SWAGGER_URL_TASK_BOUND);
        }
        swaggerUrlMapper.deleteById(id);
    }

    private ApiSwaggerUrl requireConfig(UUID projectId, UUID id) {
        ApiSwaggerUrl config = swaggerUrlMapper.selectById(id);
        if (config == null || !config.getProjectId().equals(projectId)) {
            throw ServiceExceptionUtil.get(API_SWAGGER_URL_NOT_FOUND);
        }
        return config;
    }

    /** 保存前校验 URL 可达且通过 SSRF 防护（复用导入链路同一防护口径） */
    private void requireReachable(String url) {
        sourceFetcher.fetch(url);
    }
}
