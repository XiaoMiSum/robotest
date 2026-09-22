package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.audit.AuditOperation;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiDataSourceTestReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentCopyReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentSortReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiHttpConfigTestReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiDataSourceTestRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvImportResultRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentIdRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentListItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentSetDefaultRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiHttpTestRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironment;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.net.http.HttpResponse;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;

import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_ENV_TASK_BOUND;

@Service
public class ApiEnvironmentServiceImpl implements ApiEnvironmentService {

    private static final String SCOPE_PROJECT = "project";

    @Resource
    private ApiEnvironmentMapper environmentMapper;
    @Resource
    private ProjectAccessGuard projectAccessGuard;
    @Resource
    private ApiScheduledTaskMapper scheduledTaskMapper;

    @Override
    public List<ApiEnvironmentListItemRespDTO> fetchEnvironments(UUID projectId, UUID workspaceId, UUID userId,
            String keyword) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        List<ApiEnvironment> environments = environmentMapper.listByProject(projectId, keyword);
        return environments.stream().map(EnvironmentEffectiveSnapshot::toListItem).toList();
    }

    @Override
    public ApiEnvironmentDetailRespDTO getEnvironment(UUID projectId, UUID workspaceId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        return EnvironmentEffectiveSnapshot.assembleDetail(requireEnv(projectId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditOperation(operation = "CREATE", entityType = "ApiEnvironment")
    public ApiEnvironmentIdRespDTO createEnvironment(UUID projectId, UUID workspaceId, UUID userId,
            ApiEnvironmentSaveReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        if (environmentMapper.existsByProjectIdAndName(projectId, reqDTO.getName(), null)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_ENV_NAME_EXISTS);
        }
        EnvironmentEffectiveSnapshot.NormalizedAggregate aggregate =
                EnvironmentEffectiveSnapshot.normalize(reqDTO);

        ApiEnvironment env = new ApiEnvironment();
        env.setId(UUID.randomUUID());
        env.setProjectId(projectId);
        env.setName(reqDTO.getName());
        env.setDescription(reqDTO.getDescription());
        env.setScope(SCOPE_PROJECT);
        env.setIsDefault(Boolean.TRUE.equals(reqDTO.getIsDefault()));
        env.setSortOrder(reqDTO.getSortOrder() != null ? reqDTO.getSortOrder() : 0);
        if (Boolean.TRUE.equals(env.getIsDefault())) {
            environmentMapper.clearDefaultByProjectId(projectId);
        }
        EnvironmentEffectiveSnapshot.applyAggregate(env, aggregate);
        environmentMapper.insert(env);
        return new ApiEnvironmentIdRespDTO(env.getId().toString());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditOperation(operation = "UPDATE", entityType = "ApiEnvironment")
    public void updateEnvironment(UUID projectId, UUID workspaceId, UUID userId, UUID id,
            ApiEnvironmentSaveReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiEnvironment existing = requireEnv(projectId, id);
        if (environmentMapper.existsByProjectIdAndName(projectId, reqDTO.getName(), id)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_ENV_NAME_EXISTS);
        }
        EnvironmentEffectiveSnapshot.NormalizedAggregate aggregate =
                EnvironmentEffectiveSnapshot.normalize(reqDTO);

        boolean promoteToDefault = Boolean.TRUE.equals(reqDTO.getIsDefault())
                && !Boolean.TRUE.equals(existing.getIsDefault());
        if (promoteToDefault) {
            environmentMapper.clearDefaultByProjectId(projectId);
        }
        ApiEnvironment update = new ApiEnvironment();
        update.setId(id);
        update.setName(reqDTO.getName());
        update.setDescription(reqDTO.getDescription());
        update.setIsDefault(Boolean.TRUE.equals(reqDTO.getIsDefault()));
        update.setSortOrder(reqDTO.getSortOrder() != null ? reqDTO.getSortOrder() : existing.getSortOrder());
        EnvironmentEffectiveSnapshot.applyAggregate(update, aggregate);
        environmentMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditOperation(operation = "DELETE", entityType = "ApiEnvironment")
    public void deleteEnvironment(UUID projectId, UUID workspaceId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireEnv(projectId, id);
        Long boundCount = scheduledTaskMapper.selectCountEnvBound(id);
        if (boundCount != null && boundCount > 0) {
            throw ServiceExceptionUtil.get(API_ENV_TASK_BOUND);
        }
        environmentMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiEnvironmentSetDefaultRespDTO setDefaultEnvironment(UUID projectId, UUID workspaceId, UUID userId,
            UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireEnv(projectId, id);

        environmentMapper.clearDefaultByProjectId(projectId);
        ApiEnvironment update = new ApiEnvironment();
        update.setId(id);
        update.setIsDefault(true);
        environmentMapper.updateById(update);
        return new ApiEnvironmentSetDefaultRespDTO(true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditOperation(operation = "CREATE", entityType = "ApiEnvironment")
    public ApiEnvironmentIdRespDTO copyEnvironment(UUID projectId, UUID workspaceId, UUID userId, UUID id,
            ApiEnvironmentCopyReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiEnvironment source = requireEnv(projectId, id);
        if (environmentMapper.existsByProjectIdAndName(projectId, reqDTO.getName(), null)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_ENV_NAME_EXISTS);
        }
        ApiEnvironmentDetailRespDTO detail = EnvironmentEffectiveSnapshot.assembleDetail(source);
        ApiEnvironment copy = new ApiEnvironment();
        copy.setId(UUID.randomUUID());
        copy.setProjectId(projectId);
        copy.setName(reqDTO.getName());
        copy.setDescription(source.getDescription());
        copy.setScope(SCOPE_PROJECT);
        // 副本始终不抢占默认标记，避免复制操作改变执行默认行为
        copy.setIsDefault(false);
        copy.setSortOrder(nextSortOrder(projectId));

        EnvironmentEffectiveSnapshot.applyAggregate(copy, EnvironmentEffectiveSnapshot.fromDetail(detail));
        environmentMapper.insert(copy);
        return new ApiEnvironmentIdRespDTO(copy.getId().toString());
    }

    @Override
    public void sortEnvironment(UUID projectId, UUID workspaceId, UUID userId, UUID id,
            ApiEnvironmentSortReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireEnv(projectId, id);

        ApiEnvironment update = new ApiEnvironment();
        update.setId(id);
        update.setSortOrder(reqDTO.getSortOrder());
        environmentMapper.updateById(update);
    }

    // ========== 连接测试（3.1.7 / 3.1.8，请求体传配置不落库） ==========

    @Override
    public ApiDataSourceTestRespDTO testDataSourceConfig(UUID projectId, UUID workspaceId, UUID userId,
            UUID id, ApiDataSourceTestReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireEnv(projectId, id);
        return EnvironmentConnectionTester.testConnection(reqDTO.getUrl(), reqDTO.getDriver(),
                reqDTO.getConnectionProperties(), this::openRedisConnection, this::openJdbcConnection);
    }

    @Override
    public ApiHttpTestRespDTO testHttpConfig(UUID projectId, UUID workspaceId, UUID userId,
            UUID id, ApiHttpConfigTestReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireEnv(projectId, id);
        return EnvironmentConnectionTester.testHttp(reqDTO.getBaseUrl(), this::executeHttpGet);
    }

    @Override
    public ApiEnvironmentDetailRespDTO exportEnvironment(UUID projectId, UUID workspaceId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiEnvironmentDetailRespDTO detail = EnvironmentEffectiveSnapshot
                .assembleDetail(requireEnv(projectId, id));
        // 数据源凭据内嵌于连接 URL 无法部分脱敏，导出必须整段排除（详细设计 3.1.9）
        detail.setDataSources(List.of());
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditOperation(operation = "CREATE", entityType = "ApiEnvironment")
    public ApiEnvImportResultRespDTO importEnvironment(UUID projectId, UUID workspaceId, UUID userId,
            MultipartFile file, boolean overwrite) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiEnvironmentDetailRespDTO payload = EnvironmentEffectiveSnapshot.parseImportPayload(file);
        if (payload.getName() == null || payload.getName().isBlank()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }

        EnvironmentEffectiveSnapshot.NormalizedAggregate aggregate =
                EnvironmentEffectiveSnapshot.fromDetail(payload);

        ApiEnvironment existing = environmentMapper.findByProjectIdAndName(projectId, payload.getName());
        if (existing != null && !overwrite) {
            return new ApiEnvImportResultRespDTO(0, 0, 1);
        }
        if (existing != null) {
            // 覆盖语义：保留原 id 与排序，仅替换内容与聚合资源
            ApiEnvironment update = new ApiEnvironment();
            update.setId(existing.getId());
            update.setDescription(payload.getDescription());
            EnvironmentEffectiveSnapshot.applyAggregate(update, aggregate);
            environmentMapper.updateById(update);
            return new ApiEnvImportResultRespDTO(0, 1, 0);
        }

        ApiEnvironment env = new ApiEnvironment();
        env.setId(UUID.randomUUID());
        env.setProjectId(projectId);
        env.setName(payload.getName());
        env.setDescription(payload.getDescription());
        env.setScope(SCOPE_PROJECT);
        env.setIsDefault(false);
        env.setSortOrder(nextSortOrder(projectId));
        EnvironmentEffectiveSnapshot.applyAggregate(env, aggregate);
        environmentMapper.insert(env);
        return new ApiEnvImportResultRespDTO(1, 0, 0);
    }

    // ========== 可测接缝（真实 I/O 下沉至 ConnectionTester，单测中以 spy 覆盖接缝本体） ==========

    /** RESP 建连接缝本体，单测中以 spy 覆盖；真实实现见 EnvironmentConnectionTester.openRedis */
    protected ApiDataSourceTestRespDTO openRedisConnection(String url) throws Exception {
        return EnvironmentConnectionTester.openRedis(url);
    }

    /** JDBC 建连接缝本体，单测中以 spy 覆盖；真实实现见 EnvironmentConnectionTester.openJdbc */
    protected Connection openJdbcConnection(String driver, String url, Properties props) throws Exception {
        return EnvironmentConnectionTester.openJdbc(driver, url, props);
    }

    /** HTTP GET 接缝本体，单测中以 spy 覆盖；真实实现见 EnvironmentConnectionTester.httpGet */
    protected HttpResponse<Void> executeHttpGet(String baseUrl) throws Exception {
        return EnvironmentConnectionTester.httpGet(baseUrl);
    }

    // ========== 环境导入导出 ==========

    private int nextSortOrder(UUID projectId) {
        List<ApiEnvironment> siblings = environmentMapper.listByProject(projectId, null);
        return siblings.stream().mapToInt(env -> env.getSortOrder() != null ? env.getSortOrder() : 0).max()
                .orElse(-1) + 1;
    }

    // ========== 内部工具 ==========

    private ApiEnvironment requireEnv(UUID projectId, UUID id) {
        ApiEnvironment env = environmentMapper.selectById(id);
        if (env == null || !Objects.equals(env.getProjectId(), projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_ENV_NOT_FOUND);
        }
        return env;
    }
}