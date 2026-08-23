package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.audit.AuditOperation;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentCopyReqDTO;
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
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentVariableRevealRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentVariableRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiHttpTestRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiDataSource;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironment;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironmentHttp;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironmentProcessor;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironmentVariable;
import io.github.xiaomisum.robotest.repository.apitest.ApiDataSourceMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentHttpMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentProcessorMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentVariableMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.util.JsonUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ApiEnvironmentServiceImpl implements ApiEnvironmentService {

    private static final String SCOPE_PROJECT = "project";
    private static final String TYPE_TEXT = "text";
    private static final String TYPE_NUMBER = "number";
    private static final String TYPE_SENSITIVE = "sensitive";
    private static final Set<String> VARIABLE_TYPES = Set.of(TYPE_TEXT, TYPE_NUMBER, TYPE_SENSITIVE);
    /** 变量名仅允许字母/数字/下划线（详细设计 3.3.1） */
    private static final java.util.regex.Pattern VARIABLE_NAME_PATTERN = java.util.regex.Pattern.compile("^[A-Za-z0-9_]+$");
    /** 仅放行随服务打包的驱动，防止任意类加载（安全约束） */
    private static final Set<String> SUPPORTED_JDBC_DRIVERS = Set.of("org.postgresql.Driver", "com.mysql.cj.MySQLDriver");
    private static final int JDBC_LOGIN_TIMEOUT_SECONDS = 10;

    @Resource
    private ApiEnvironmentMapper environmentMapper;
    @Resource
    private ApiEnvironmentHttpMapper httpMapper;
    @Resource
    private ApiEnvironmentVariableMapper variableMapper;
    @Resource
    private ApiDataSourceMapper dataSourceMapper;
    @Resource
    private ApiEnvironmentProcessorMapper processorMapper;
    @Resource
    private ProjectAccessGuard projectAccessGuard;

    /** 敏感变量加密密钥（Base64 编码 32 字节），未配置时保存敏感值直接失败（详细设计 6.2 强制加密） */
    @Value("${robotest.env.secret-key:}")
    private String secretKeyBase64;

    @Override
    public List<ApiEnvironmentListItemRespDTO> fetchEnvironments(UUID projectId, UUID workspaceId, UUID userId,
            String keyword) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);

        List<ApiEnvironment> environments = environmentMapper.listByProject(projectId, keyword);
        Map<UUID, Long> variableCounts = variableMapper.countGroupByEnvironment();
        Map<UUID, Long> dataSourceCounts = dataSourceMapper.countGroupByEnvironment();
        Map<UUID, Long> processorCounts = processorMapper.countGroupByEnvironment();

        return environments.stream().map(env -> {
            ApiEnvironmentListItemRespDTO item = new ApiEnvironmentListItemRespDTO();
            item.setId(env.getId().toString());
            item.setName(env.getName());
            item.setDescription(env.getDescription());
            item.setIsDefault(env.getIsDefault());
            item.setSortOrder(env.getSortOrder());
            item.setVariableCount(variableCounts.getOrDefault(env.getId(), 0L));
            item.setDataSourceCount(dataSourceCounts.getOrDefault(env.getId(), 0L));
            item.setProcessorCount(processorCounts.getOrDefault(env.getId(), 0L));
            return item;
        }).toList();
    }

    @Override
    public ApiEnvironmentDetailRespDTO getEnvironment(UUID projectId, UUID workspaceId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiEnvironment env = requireEnv(projectId, id);
        return assembleDetail(env);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditOperation(operation = "CREATE", entityType = "ApiEnvironment")
    public ApiEnvironmentIdRespDTO createEnvironment(UUID projectId, UUID workspaceId, UUID userId,
            ApiEnvironmentSaveReqDTO reqDTO) {
        projectAccessGuard.requireProjectMaintainer(projectId, workspaceId, userId);
        if (environmentMapper.existsByProjectIdAndName(projectId, reqDTO.getName(), null)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_ENV_NAME_EXISTS);
        }
        NormalizedAggregate aggregate = normalize(reqDTO);

        ApiEnvironment env = new ApiEnvironment();
        // 显式生成主键：子资源行在 insert 返回前即需引用 environment_id
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
        environmentMapper.insert(env);

        saveChildren(env.getId(), aggregate);
        return new ApiEnvironmentIdRespDTO(env.getId().toString());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditOperation(operation = "UPDATE", entityType = "ApiEnvironment")
    public void updateEnvironment(UUID projectId, UUID workspaceId, UUID userId, UUID id,
            ApiEnvironmentSaveReqDTO reqDTO) {
        projectAccessGuard.requireProjectMaintainer(projectId, workspaceId, userId);
        ApiEnvironment existing = requireEnv(projectId, id);
        if (environmentMapper.existsByProjectIdAndName(projectId, reqDTO.getName(), id)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_ENV_NAME_EXISTS);
        }
        // 旧敏感密文须在子资源删除前读取，供「留空不修改」沿用
        Map<String, String> previousSensitives = loadSensitiveCipherByName(id);
        NormalizedAggregate aggregate = normalize(reqDTO, previousSensitives);

        boolean promoteToDefault = Boolean.TRUE.equals(reqDTO.getIsDefault())
                && !Boolean.TRUE.equals(existing.getIsDefault());
        if (promoteToDefault) {
            environmentMapper.clearDefaultByProjectId(projectId);
        }
        // 查询仅做校验，更新载体只携带变更字段（部分更新原则）
        ApiEnvironment update = new ApiEnvironment();
        update.setId(id);
        update.setName(reqDTO.getName());
        update.setDescription(reqDTO.getDescription());
        update.setIsDefault(Boolean.TRUE.equals(reqDTO.getIsDefault()));
        update.setSortOrder(reqDTO.getSortOrder() != null ? reqDTO.getSortOrder() : existing.getSortOrder());
        environmentMapper.updateById(update);

        replaceChildren(id, aggregate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditOperation(operation = "DELETE", entityType = "ApiEnvironment")
    public void deleteEnvironment(UUID projectId, UUID workspaceId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMaintainer(projectId, workspaceId, userId);
        requireEnv(projectId, id);
        // 删除保护（7402 场景引用 / 7404 定时任务绑定）的引用方模块尚未实现，
        // 校验点预留于此，测试场景/定时任务模块落地时在下方补齐引用检查
        deleteChildren(id);
        environmentMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiEnvironmentSetDefaultRespDTO setDefaultEnvironment(UUID projectId, UUID workspaceId, UUID userId,
            UUID id) {
        projectAccessGuard.requireProjectMaintainer(projectId, workspaceId, userId);
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
        projectAccessGuard.requireProjectMaintainer(projectId, workspaceId, userId);
        ApiEnvironment source = requireEnv(projectId, id);
        if (environmentMapper.existsByProjectIdAndName(projectId, reqDTO.getName(), null)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_ENV_NAME_EXISTS);
        }
        ApiEnvironmentDetailRespDTO detail = assembleDetail(source);
        List<ApiEnvironment> siblings = environmentMapper.listByProject(projectId, null);
        int nextSort = siblings.stream().mapToInt(env -> env.getSortOrder() != null ? env.getSortOrder() : 0).max()
                .orElse(-1) + 1;

        ApiEnvironment copy = new ApiEnvironment();
        // 显式生成主键：子资源行在 insert 返回前即需引用 environment_id
        copy.setId(UUID.randomUUID());
        copy.setProjectId(projectId);
        copy.setName(reqDTO.getName());
        copy.setDescription(source.getDescription());
        copy.setScope(SCOPE_PROJECT);
        // 副本始终不抢占默认标记，避免复制操作改变执行默认行为
        copy.setIsDefault(false);
        copy.setSortOrder(nextSort);
        environmentMapper.insert(copy);

        NormalizedAggregate aggregate = fromDetail(detail);
        saveChildren(copy.getId(), aggregate);
        return new ApiEnvironmentIdRespDTO(copy.getId().toString());
    }

    @Override
    public void sortEnvironment(UUID projectId, UUID workspaceId, UUID userId, UUID id,
            ApiEnvironmentSortReqDTO reqDTO) {
        projectAccessGuard.requireProjectMaintainer(projectId, workspaceId, userId);
        requireEnv(projectId, id);

        ApiEnvironment update = new ApiEnvironment();
        update.setId(id);
        update.setSortOrder(reqDTO.getSortOrder());
        environmentMapper.updateById(update);
    }

    // ========== 处理器子资源（3.2） ==========

    @Override
    public List<ApiEnvironmentProcessorRespDTO> listProcessors(UUID projectId, UUID workspaceId, UUID userId,
            UUID id, String processorType) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireEnv(projectId, id);
        return processorMapper.listByEnvironmentIdAndType(id, processorType).stream()
                .map(this::toProcessorResp).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditOperation(operation = "CREATE", entityType = "ApiEnvironmentProcessor")
    public ApiEnvironmentProcessorRespDTO createProcessor(UUID projectId, UUID workspaceId, UUID userId,
            UUID id, ApiEnvironmentProcessorSaveReqDTO reqDTO) {
        projectAccessGuard.requireProjectMaintainer(projectId, workspaceId, userId);
        requireEnv(projectId, id);
        validateProcessorType(reqDTO.getProcessorType());

        ApiEnvironmentProcessor row = new ApiEnvironmentProcessor();
        // 显式生成主键：insert 返回前即需构建响应体
        row.setId(UUID.randomUUID());
        row.setEnvironmentId(id);
        applyProcessorFields(row, reqDTO);
        processorMapper.insert(row);
        return toProcessorResp(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditOperation(operation = "UPDATE", entityType = "ApiEnvironmentProcessor")
    public ApiEnvironmentProcessorRespDTO updateProcessor(UUID projectId, UUID workspaceId, UUID userId,
            UUID id, UUID procId, ApiEnvironmentProcessorSaveReqDTO reqDTO) {
        projectAccessGuard.requireProjectMaintainer(projectId, workspaceId, userId);
        requireEnv(projectId, id);
        validateProcessorType(reqDTO.getProcessorType());
        ApiEnvironmentProcessor row = requireProcessor(id, procId);

        // PUT 语义：全量覆盖处理器内容字段，归属环境不变
        applyProcessorFields(row, reqDTO);
        processorMapper.updateById(row);
        return toProcessorResp(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditOperation(operation = "DELETE", entityType = "ApiEnvironmentProcessor")
    public void deleteProcessor(UUID projectId, UUID workspaceId, UUID userId, UUID id, UUID procId) {
        projectAccessGuard.requireProjectMaintainer(projectId, workspaceId, userId);
        requireEnv(projectId, id);
        requireProcessor(id, procId);
        processorMapper.deleteById(procId);
    }

    // ========== 变量子资源（3.3） ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditOperation(operation = "UPDATE", entityType = "ApiEnvironmentVariable")
    public List<ApiEnvironmentVariableRespDTO> batchReplaceVariables(UUID projectId, UUID workspaceId, UUID userId,
            UUID id, ApiEnvironmentVariableBatchReqDTO reqDTO) {
        projectAccessGuard.requireProjectMaintainer(projectId, workspaceId, userId);
        requireEnv(projectId, id);

        // 旧敏感密文须在删除前读取，供「留空不修改」沿用
        Map<String, String> previousSensitives = loadSensitiveCipherByName(id);
        List<ApiEnvironmentVariable> rows = normalizeVariables(reqDTO.getVariables(), previousSensitives);
        rows.forEach(row -> row.setEnvironmentId(id));
        variableMapper.deleteByEnvironmentId(id);
        if (!rows.isEmpty()) {
            variableMapper.insertBatch(rows);
        }
        return listMaskedVariables(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditOperation(operation = "CREATE", entityType = "ApiEnvironmentVariable")
    public ApiEnvironmentVariableRespDTO addVariableFromResult(UUID projectId, UUID workspaceId, UUID userId,
            UUID id, ApiEnvironmentVariableCreateReqDTO reqDTO) {
        projectAccessGuard.requireProjectMaintainer(projectId, workspaceId, userId);
        requireEnv(projectId, id);
        if (variableMapper.findByEnvironmentIdAndName(id, reqDTO.getName()) != null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_ENV_VARIABLE_EXISTS);
        }
        ApiEnvironmentSaveReqDTO.Variable source = new ApiEnvironmentSaveReqDTO.Variable();
        source.setName(reqDTO.getName());
        source.setValue(reqDTO.getValue());
        source.setType(reqDTO.getType());
        source.setDescription(reqDTO.getDescription());

        ApiEnvironmentVariable row = normalizeVariables(List.of(source)).getFirst();
        // 显式生成主键：insert 返回前即需构建响应体
        row.setId(UUID.randomUUID());
        row.setEnvironmentId(id);
        row.setSourceStepId(parseUuidOrNull(reqDTO.getSourceStepId()));
        row.setSourceReportId(parseUuidOrNull(reqDTO.getSourceReportId()));
        variableMapper.insert(row);
        return toMaskedVariableResp(row.getId(), row.getName(), row.getValue(), row.getType(),
                row.getDescription(), hasText(row.getValue()), null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiEnvImportResultRespDTO importVariables(UUID projectId, UUID workspaceId, UUID userId,
            UUID id, ApiEnvironmentVariableImportReqDTO reqDTO) {
        projectAccessGuard.requireProjectMaintainer(projectId, workspaceId, userId);
        requireEnv(projectId, id);

        List<ApiEnvironmentVariable> incoming = normalizeVariables(reqDTO.getVariables());
        Map<String, ApiEnvironmentVariable> existingByName = variableMapper.listByEnvironmentId(id).stream()
                .collect(Collectors.toMap(ApiEnvironmentVariable::getName, row -> row));
        boolean overwrite = Boolean.TRUE.equals(reqDTO.getOverwrite());

        int created = 0;
        int overwritten = 0;
        int skipped = 0;
        for (ApiEnvironmentVariable incomingRow : incoming) {
            ApiEnvironmentVariable existing = existingByName.get(incomingRow.getName());
            if (existing == null) {
                incomingRow.setEnvironmentId(id);
                variableMapper.insert(incomingRow);
                created++;
            } else if (overwrite) {
                // 部分更新载体：只携带本次导入的字段（C9）
                ApiEnvironmentVariable update = new ApiEnvironmentVariable();
                update.setId(existing.getId());
                update.setType(incomingRow.getType());
                update.setDescription(incomingRow.getDescription());
                update.setValue(incomingRow.getValue());
                variableMapper.updateById(update);
                overwritten++;
            } else {
                skipped++;
            }
        }
        return new ApiEnvImportResultRespDTO(created, overwritten, skipped);
    }

    @Override
    public List<ApiEnvironmentVariableRespDTO> exportVariables(UUID projectId, UUID workspaceId, UUID userId,
            UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireEnv(projectId, id);
        return listMaskedVariables(id);
    }

    @Override
    public ApiEnvironmentVariableRevealRespDTO revealVariable(UUID projectId, UUID workspaceId, UUID userId,
            UUID id, UUID variableId) {
        projectAccessGuard.requireProjectMaintainer(projectId, workspaceId, userId);
        requireEnv(projectId, id);
        ApiEnvironmentVariable row = requireVariable(id, variableId);

        String plain = row.getValue();
        if (plain != null && TYPE_SENSITIVE.equals(row.getType())) {
            byte[] key = requireCipherKey();
            plain = SecretCryptoUtil.decrypt(key, plain);
        }
        return new ApiEnvironmentVariableRevealRespDTO(row.getId().toString(), row.getName(), plain);
    }

    // ========== 连接测试（3.1.7 / 3.1.8） ==========

    @Override
    public ApiDataSourceTestRespDTO testDataSource(UUID projectId, UUID workspaceId, UUID userId,
            UUID id, UUID dataSourceId) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireEnv(projectId, id);
        ApiDataSource ds = requireDataSource(id, dataSourceId);
        if (!SUPPORTED_JDBC_DRIVERS.contains(ds.getDriver())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_DATASOURCE_CONN_FAILED,
                    "不支持的数据库驱动：" + ds.getDriver());
        }

        Properties props = new Properties();
        for (Map.Entry<String, Object> entry : ds.getConnectionProperties().entrySet()) {
            if (entry.getValue() != null && entry.getKey() instanceof String keyName) {
                props.put(keyName, String.valueOf(entry.getValue()));
            }
        }
        try (Connection connection = openJdbcConnection(ds.getDriver(), ds.getUrl(), props)) {
            DatabaseMetaData meta = connection.getMetaData();
            String version = meta.getDatabaseProductName() + " " + meta.getDatabaseMajorVersion()
                    + "." + meta.getDatabaseMinorVersion();
            return new ApiDataSourceTestRespDTO(true, "连接成功", version);
        } catch (Exception e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_DATASOURCE_CONN_FAILED,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    /** 可测接缝：真实 JDBC 建连逻辑，单测中以 spy 覆盖 */
    protected Connection openJdbcConnection(String driver, String url, Properties props) throws Exception {
        Class.forName(driver);
        DriverManager.setLoginTimeout(JDBC_LOGIN_TIMEOUT_SECONDS);
        return DriverManager.getConnection(url, props);
    }

    @Override
    public ApiHttpTestRespDTO testHttpConfig(UUID projectId, UUID workspaceId, UUID userId,
            UUID id, UUID httpConfigId) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireEnv(projectId, id);
        ApiEnvironmentHttp config = requireHttpConfig(id, httpConfigId);

        long start = System.currentTimeMillis();
        try {
            HttpResponse<Void> response = executeHttpGet(config);
            long durationMs = System.currentTimeMillis() - start;
            // 任意 HTTP 响应均视为连通（含 4xx/5xx），网络层失败才走 success=false 分支
            return new ApiHttpTestRespDTO(true, "连接成功", response.statusCode(), durationMs);
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - start;
            String message = e.getCause() != null && e.getCause().getMessage() != null
                    ? e.getCause().getMessage() : e.getMessage();
            return new ApiHttpTestRespDTO(false,
                    message == null ? e.getClass().getSimpleName() : message, null, durationMs);
        }
    }

    /** 可测接缝：真实 HTTP GET 逻辑，单测中以 spy 覆盖 */
    protected HttpResponse<Void> executeHttpGet(ApiEnvironmentHttp config) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs() != null
                        ? config.getConnectTimeoutMs() : 10000))
                .followRedirects(Boolean.FALSE.equals(config.getFollowRedirects())
                        ? HttpClient.Redirect.NEVER : HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.getBaseUrl()))
                .timeout(Duration.ofMillis(config.getTimeoutMs() != null ? config.getTimeoutMs() : 30000))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.discarding());
    }

    // ========== 环境导入导出（3.1.9 / 3.1.10） ==========

    @Override
    public ApiEnvironmentDetailRespDTO exportEnvironment(UUID projectId, UUID workspaceId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        // 详情层已对敏感值掩码化，导出与详情同构即可满足「敏感字段脱敏」要求
        return assembleDetail(requireEnv(projectId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditOperation(operation = "CREATE", entityType = "ApiEnvironment")
    public ApiEnvImportResultRespDTO importEnvironment(UUID projectId, UUID workspaceId, UUID userId,
            MultipartFile file, boolean overwrite) {
        projectAccessGuard.requireProjectMaintainer(projectId, workspaceId, userId);
        ApiEnvironmentDetailRespDTO payload = parseImportPayload(file);
        if (payload.getName() == null || payload.getName().isBlank()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }

        NormalizedAggregate aggregate = fromDetail(payload);

        ApiEnvironment existing = environmentMapper.findByProjectIdAndName(projectId, payload.getName());
        if (existing != null && !overwrite) {
            return new ApiEnvImportResultRespDTO(0, 0, 1);
        }
        int nextSort = nextSortOrder(projectId);
        if (existing != null) {
            // 覆盖语义：保留原 id 与排序，仅替换内容与子资源
            ApiEnvironment update = new ApiEnvironment();
            update.setId(existing.getId());
            update.setDescription(payload.getDescription());
            environmentMapper.updateById(update);
            replaceChildren(existing.getId(), aggregate);
            return new ApiEnvImportResultRespDTO(0, 1, 0);
        }

        ApiEnvironment env = new ApiEnvironment();
        env.setId(UUID.randomUUID());
        env.setProjectId(projectId);
        env.setName(payload.getName());
        env.setDescription(payload.getDescription());
        env.setScope(SCOPE_PROJECT);
        env.setIsDefault(false);
        env.setSortOrder(nextSort);
        environmentMapper.insert(env);
        saveChildren(env.getId(), aggregate);
        return new ApiEnvImportResultRespDTO(1, 0, 0);
    }

    private ApiEnvironmentDetailRespDTO parseImportPayload(MultipartFile file) {
        try {
            String json = new String(file.getBytes(), StandardCharsets.UTF_8);
            ApiEnvironmentDetailRespDTO payload = JsonUtils.parseObject(json, ApiEnvironmentDetailRespDTO.class);
            if (payload == null) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
            }
            return payload;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
    }

    private int nextSortOrder(UUID projectId) {
        List<ApiEnvironment> siblings = environmentMapper.listByProject(projectId, null);
        return siblings.stream().mapToInt(env -> env.getSortOrder() != null ? env.getSortOrder() : 0).max()
                .orElse(-1) + 1;
    }

    // ========== 聚合组装与归一化 ==========

    private ApiEnvironment requireEnv(UUID projectId, UUID id) {
        ApiEnvironment env = environmentMapper.selectById(id);
        if (env == null || !Objects.equals(env.getProjectId(), projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_ENV_NOT_FOUND);
        }
        return env;
    }

    private ApiEnvironmentDetailRespDTO assembleDetail(ApiEnvironment env) {
        ApiEnvironmentDetailRespDTO detail = new ApiEnvironmentDetailRespDTO();
        detail.setId(env.getId().toString());
        detail.setName(env.getName());
        detail.setDescription(env.getDescription());
        detail.setScope(env.getScope());
        detail.setIsDefault(env.getIsDefault());
        detail.setSortOrder(env.getSortOrder());

        detail.setHttpConfigs(httpMapper.listByEnvironmentId(env.getId()).stream().map(row -> {
            ApiEnvironmentDetailRespDTO.HttpConfig config = new ApiEnvironmentDetailRespDTO.HttpConfig();
            config.setId(row.getId().toString());
            config.setName(row.getName());
            config.setRefName(row.getRefName());
            config.setBaseUrl(row.getBaseUrl());
            config.setDefaultMethod(row.getDefaultMethod());
            config.setHeaders(convertHeaders(row.getDefaultHeaders()));
            config.setTimeoutMs(row.getTimeoutMs());
            config.setConnectTimeoutMs(row.getConnectTimeoutMs());
            config.setFollowRedirects(row.getFollowRedirects());
            config.setVerifySsl(row.getVerifySsl());
            config.setIsDefault(row.getIsDefault());
            return config;
        }).toList());

        // 仅敏感变量输出掩码（交互设计 3.3）；普通变量回显原值供编辑，hasValue 标识是否已配置
        detail.setVariables(variableMapper.listByEnvironmentId(env.getId()).stream().map(row -> {
            ApiEnvironmentDetailRespDTO.Variable variable = new ApiEnvironmentDetailRespDTO.Variable();
            variable.setId(row.getId().toString());
            variable.setName(row.getName());
            boolean hasValue = row.getValue() != null && !row.getValue().isEmpty();
            boolean sensitive = TYPE_SENSITIVE.equals(row.getType());
            if (!hasValue) {
                variable.setValue(null);
            } else {
                variable.setValue(sensitive ? ApiEnvironmentDetailRespDTO.SENSITIVE_MASK : row.getValue());
            }
            variable.setHasValue(hasValue);
            variable.setDescription(row.getDescription());
            variable.setType(row.getType());
            return variable;
        }).toList());

        detail.setDataSources(dataSourceMapper.listByEnvironmentId(env.getId()).stream().map(row -> {
            ApiEnvironmentDetailRespDTO.DataSource ds = new ApiEnvironmentDetailRespDTO.DataSource();
            ds.setId(row.getId().toString());
            ds.setName(row.getName());
            ds.setRefName(row.getRefName());
            ds.setDriver(row.getDriver());
            ds.setUrl(row.getUrl());
            ds.setConnectionProperties(row.getConnectionProperties());
            ds.setMaxPoolSize(row.getMaxPoolSize());
            return ds;
        }).toList());

        detail.setProcessors(processorMapper.listByEnvironmentIdAndType(env.getId(), null).stream().map(row -> {
            ApiEnvironmentDetailRespDTO.Processor processor = new ApiEnvironmentDetailRespDTO.Processor();
            processor.setId(row.getId().toString());
            processor.setProcessorType(row.getProcessorType());
            processor.setName(row.getName());
            processor.setConfig(row.getConfig());
            processor.setSortOrder(row.getSortOrder());
            processor.setEnabled(row.getEnabled());
            return processor;
        }).toList());
        return detail;
    }

    private List<ApiEnvironmentDetailRespDTO.HeaderItem> convertHeaders(List<Map<String, Object>> rows) {
        if (rows == null) {
            return List.of();
        }
        return rows.stream().map(row -> {
            ApiEnvironmentDetailRespDTO.HeaderItem item = new ApiEnvironmentDetailRespDTO.HeaderItem();
            item.setKey((String) row.get("key"));
            item.setValue((String) row.get("value"));
            Object enabled = row.get("enabled");
            item.setEnabled(enabled instanceof Boolean flag ? flag : true);
            return item;
        }).toList();
    }

    /**
     * 归一化并校验聚合子资源：HTTP 配置缺省自动生成默认配置、每环境唯一默认配置、
     * ref_name 缺省按 http_N 生成、变量名/类型/取值校验与敏感值加密
     */
    private NormalizedAggregate normalize(ApiEnvironmentSaveReqDTO reqDTO) {
        return normalize(reqDTO, null);
    }

    private NormalizedAggregate normalize(ApiEnvironmentSaveReqDTO reqDTO,
            Map<String, String> previousSensitives) {
        NormalizedAggregate aggregate = new NormalizedAggregate();

        List<ApiEnvironmentSaveReqDTO.HttpConfig> httpConfigs = reqDTO.getHttpConfigs();
        if (httpConfigs == null || httpConfigs.isEmpty()) {
            httpConfigs = List.of(defaultHttpConfig());
        }
        int defaultIdx = -1;
        for (int i = 0; i < httpConfigs.size(); i++) {
            if (Boolean.TRUE.equals(httpConfigs.get(i).getIsDefault())) {
                if (defaultIdx >= 0) {
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
                }
                defaultIdx = i;
            }
        }
        for (int i = 0; i < httpConfigs.size(); i++) {
            ApiEnvironmentSaveReqDTO.HttpConfig source = httpConfigs.get(i);
            ApiEnvironmentHttp row = new ApiEnvironmentHttp();
            row.setName(source.getName());
            row.setRefName(source.getRefName() != null && !source.getRefName().isBlank()
                    ? source.getRefName() : "http_" + (i + 1));
            row.setBaseUrl(source.getBaseUrl());
            row.setDefaultMethod(source.getDefaultMethod());
            row.setDefaultHeaders(normalizeHeaders(source.getHeaders()));
            row.setTimeoutMs(source.getTimeoutMs() != null ? source.getTimeoutMs() : 30000);
            row.setConnectTimeoutMs(source.getConnectTimeoutMs() != null ? source.getConnectTimeoutMs() : 10000);
            row.setFollowRedirects(!Boolean.FALSE.equals(source.getFollowRedirects()));
            row.setVerifySsl(!Boolean.FALSE.equals(source.getVerifySsl()));
            row.setIsDefault(i == defaultIdx || (defaultIdx < 0 && i == 0));
            aggregate.httpConfigs.add(row);
        }

        if (reqDTO.getVariables() != null) {
            aggregate.variables.addAll(normalizeVariables(reqDTO.getVariables(), previousSensitives));
        }

        if (reqDTO.getDataSources() != null) {
            for (ApiEnvironmentSaveReqDTO.DataSource source : reqDTO.getDataSources()) {
                ApiDataSource row = new ApiDataSource();
                row.setName(source.getName());
                row.setRefName(source.getRefName());
                row.setDriver(source.getDriver());
                row.setUrl(source.getUrl());
                row.setConnectionProperties(source.getConnectionProperties() != null
                        ? source.getConnectionProperties() : Map.of());
                row.setMaxPoolSize(source.getMaxPoolSize() != null ? source.getMaxPoolSize() : 5);
                aggregate.dataSources.add(row);
            }
        }

        if (reqDTO.getProcessors() != null) {
            for (ApiEnvironmentSaveReqDTO.Processor source : reqDTO.getProcessors()) {
                if (!ApiEnvironmentProcessor.TYPE_PRE.equals(source.getProcessorType())
                        && !ApiEnvironmentProcessor.TYPE_POST.equals(source.getProcessorType())) {
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
                }
                ApiEnvironmentProcessor row = new ApiEnvironmentProcessor();
                row.setProcessorType(source.getProcessorType());
                row.setName(source.getName());
                row.setConfig(source.getConfig() != null ? source.getConfig() : Map.of());
                row.setSortOrder(source.getSortOrder() != null ? source.getSortOrder() : 0);
                row.setEnabled(!Boolean.FALSE.equals(source.getEnabled()));
                aggregate.processors.add(row);
            }
        }
        return aggregate;
    }

    /**
     * 归一化并校验变量列表（聚合保存与 3.3 变量子资源共用）：
     * 名称仅字母/数字/下划线且同批唯一、类型白名单、number 取值校验、敏感值加密
     */
    private List<ApiEnvironmentVariable> normalizeVariables(List<ApiEnvironmentSaveReqDTO.Variable> sources) {
        return normalizeVariables(sources, null);
    }

    private List<ApiEnvironmentVariable> normalizeVariables(List<ApiEnvironmentSaveReqDTO.Variable> sources,
            Map<String, String> previousSensitives) {
        List<ApiEnvironmentVariable> rows = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (ApiEnvironmentSaveReqDTO.Variable source : sources) {
            if (source.getName() == null || !VARIABLE_NAME_PATTERN.matcher(source.getName()).matches()) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
            }
            String type = source.getType() == null || source.getType().isBlank() ? TYPE_TEXT : source.getType();
            if (!VARIABLE_TYPES.contains(type)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
            }
            if (!names.add(source.getName())) {
                // 变量重名：同环境内变量名唯一
                throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
            }
            if (TYPE_NUMBER.equals(type) && source.getValue() != null && !source.getValue().isBlank()
                    && !isNumeric(source.getValue())) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
            }
            ApiEnvironmentVariable row = new ApiEnvironmentVariable();
            row.setName(source.getName());
            row.setDescription(source.getDescription());
            row.setType(type);
            String value = source.getValue();
            boolean maskedOrBlank = !hasText(value) || ApiEnvironmentDetailRespDTO.SENSITIVE_MASK.equals(value);
            if (TYPE_SENSITIVE.equals(type) && maskedOrBlank && previousSensitives != null
                    && previousSensitives.containsKey(source.getName())) {
                // 交互设计 3.3「已配置（留空不修改）」：沿用旧密文，掩码字面量永不落库
                row.setValue(previousSensitives.get(source.getName()));
            } else if (hasText(value)) {
                if (TYPE_SENSITIVE.equals(type)) {
                    value = SecretCryptoUtil.encrypt(requireCipherKey(), value);
                }
                row.setValue(value);
            }
            rows.add(row);
        }
        return rows;
    }

    private byte[] requireCipherKey() {
        byte[] key = SecretCryptoUtil.parseKey(secretKeyBase64);
        if (key == null) {
            // 密钥缺失属服务端配置缺陷，非业务异常，快速失败避免明文落库
            throw new IllegalStateException("环境敏感值加密密钥未配置（robotest.env.secret-key）");
        }
        return key;
    }

    private boolean isNumeric(String value) {
        try {
            new BigDecimal(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }

    private UUID parseUuidOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void validateProcessorType(String processorType) {
        if (!ApiEnvironmentProcessor.TYPE_PRE.equals(processorType)
                && !ApiEnvironmentProcessor.TYPE_POST.equals(processorType)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
    }

    private void applyProcessorFields(ApiEnvironmentProcessor row, ApiEnvironmentProcessorSaveReqDTO reqDTO) {
        row.setProcessorType(reqDTO.getProcessorType());
        row.setName(reqDTO.getName());
        row.setConfig(reqDTO.getConfig() != null ? reqDTO.getConfig() : Map.of());
        row.setSortOrder(reqDTO.getSortOrder() != null ? reqDTO.getSortOrder() : 0);
        row.setEnabled(!Boolean.FALSE.equals(reqDTO.getEnabled()));
    }

    private ApiEnvironmentProcessorRespDTO toProcessorResp(ApiEnvironmentProcessor row) {
        ApiEnvironmentProcessorRespDTO resp = new ApiEnvironmentProcessorRespDTO();
        resp.setId(row.getId().toString());
        resp.setProcessorType(row.getProcessorType());
        resp.setName(row.getName());
        resp.setConfig(row.getConfig());
        resp.setSortOrder(row.getSortOrder());
        resp.setEnabled(row.getEnabled());
        return resp;
    }

    private ApiEnvironmentProcessor requireProcessor(UUID environmentId, UUID procId) {
        ApiEnvironmentProcessor row = processorMapper.selectById(procId);
        if (row == null || !environmentId.equals(row.getEnvironmentId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_ENV_PROCESSOR_NOT_FOUND);
        }
        return row;
    }

    private ApiEnvironmentVariable requireVariable(UUID environmentId, UUID variableId) {
        ApiEnvironmentVariable row = variableMapper.selectById(variableId);
        if (row == null || !environmentId.equals(row.getEnvironmentId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_ENV_VARIABLE_NOT_FOUND);
        }
        return row;
    }

    private ApiDataSource requireDataSource(UUID environmentId, UUID dataSourceId) {
        ApiDataSource row = dataSourceMapper.selectById(dataSourceId);
        if (row == null || !environmentId.equals(row.getEnvironmentId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_ENV_DATASOURCE_NOT_FOUND);
        }
        return row;
    }

    private ApiEnvironmentHttp requireHttpConfig(UUID environmentId, UUID httpConfigId) {
        ApiEnvironmentHttp row = httpMapper.selectById(httpConfigId);
        if (row == null || !environmentId.equals(row.getEnvironmentId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_ENV_HTTP_CONFIG_NOT_FOUND);
        }
        return row;
    }

    /** 现存敏感变量密文按名索引，用于全量替换时的「留空不修改」沿用 */
    private Map<String, String> loadSensitiveCipherByName(UUID environmentId) {
        return variableMapper.listByEnvironmentId(environmentId).stream()
                .filter(row -> TYPE_SENSITIVE.equals(row.getType()) && hasText(row.getValue()))
                .collect(Collectors.toMap(ApiEnvironmentVariable::getName, ApiEnvironmentVariable::getValue));
    }

    /** 变量列表脱敏视图：敏感值恒掩码，hasValue 标识是否已配置 */
    private List<ApiEnvironmentVariableRespDTO> listMaskedVariables(UUID environmentId) {
        return variableMapper.listByEnvironmentId(environmentId).stream()
                .map(row -> toMaskedVariableResp(row.getId(), row.getName(), row.getValue(), row.getType(),
                        row.getDescription(), hasText(row.getValue()), row.getSourceStepId(), row.getSourceReportId()))
                .toList();
    }

    private ApiEnvironmentVariableRespDTO toMaskedVariableResp(UUID id, String name, String value, String type,
            String description, boolean hasValue, UUID sourceStepId, UUID sourceReportId) {
        ApiEnvironmentVariableRespDTO resp = new ApiEnvironmentVariableRespDTO();
        resp.setId(id.toString());
        resp.setName(name);
        boolean sensitive = TYPE_SENSITIVE.equals(type);
        if (sensitive) {
            resp.setValue(hasValue ? ApiEnvironmentDetailRespDTO.SENSITIVE_MASK : null);
        } else {
            resp.setValue(value);
        }
        resp.setHasValue(hasValue);
        resp.setType(type);
        resp.setDescription(description);
        resp.setSourceStepId(sourceStepId != null ? sourceStepId.toString() : null);
        resp.setSourceReportId(sourceReportId != null ? sourceReportId.toString() : null);
        return resp;
    }

    private ApiEnvironmentSaveReqDTO.HttpConfig defaultHttpConfig() {
        ApiEnvironmentSaveReqDTO.HttpConfig config = new ApiEnvironmentSaveReqDTO.HttpConfig();
        config.setName("默认配置");
        config.setRefName("default");
        config.setBaseUrl("http://localhost");
        config.setIsDefault(true);
        return config;
    }

    private List<Map<String, Object>> normalizeHeaders(List<ApiEnvironmentSaveReqDTO.HeaderItem> headers) {
        if (headers == null) {
            return List.of();
        }
        return headers.stream().map(header -> {
            Map<String, Object> row = new HashMap<>();
            row.put("key", header.getKey());
            row.put("value", header.getValue());
            row.put("enabled", !Boolean.FALSE.equals(header.getEnabled()));
            return row;
        }).collect(Collectors.toCollection(ArrayList::new));
    }

    /** 复制/导入场景：从环境详情反向构建归一化聚合；敏感值已在详情层掩码化，此处还原为「未配置」 */
    private NormalizedAggregate fromDetail(ApiEnvironmentDetailRespDTO detail) {
        ApiEnvironmentSaveReqDTO reqDTO = new ApiEnvironmentSaveReqDTO();
        // 导入文件的段落可缺省，统一按空列表处理
        reqDTO.setHttpConfigs((detail.getHttpConfigs() != null ? detail.getHttpConfigs() : List.<ApiEnvironmentDetailRespDTO.HttpConfig>of())
                .stream().map(config -> {
            ApiEnvironmentSaveReqDTO.HttpConfig source = new ApiEnvironmentSaveReqDTO.HttpConfig();
            source.setName(config.getName());
            source.setRefName(config.getRefName());
            source.setBaseUrl(config.getBaseUrl());
            source.setDefaultMethod(config.getDefaultMethod());
            source.setHeaders(config.getHeaders().stream().map(header -> {
                ApiEnvironmentSaveReqDTO.HeaderItem item = new ApiEnvironmentSaveReqDTO.HeaderItem();
                item.setKey(header.getKey());
                item.setValue(header.getValue());
                item.setEnabled(header.getEnabled());
                return item;
            }).toList());
            source.setTimeoutMs(config.getTimeoutMs());
            source.setConnectTimeoutMs(config.getConnectTimeoutMs());
            source.setFollowRedirects(config.getFollowRedirects());
            source.setVerifySsl(config.getVerifySsl());
            source.setIsDefault(config.getIsDefault());
            return source;
        }).toList());
        // 敏感值掩码不可回写为真实值，副本中一律视为未配置，需重新填写
        reqDTO.setVariables((detail.getVariables() != null ? detail.getVariables()
                : List.<ApiEnvironmentDetailRespDTO.Variable>of()).stream().map(variable -> {
            ApiEnvironmentSaveReqDTO.Variable source = new ApiEnvironmentSaveReqDTO.Variable();
            source.setName(variable.getName());
            source.setDescription(variable.getDescription());
            source.setType(variable.getType());
            if (!TYPE_SENSITIVE.equals(variable.getType()) && Boolean.TRUE.equals(variable.getHasValue())
                    && !ApiEnvironmentDetailRespDTO.SENSITIVE_MASK.equals(variable.getValue())) {
                source.setValue(variable.getValue());
            }
            return source;
        }).toList());
        reqDTO.setProcessors((detail.getProcessors() != null ? detail.getProcessors()
                : List.<ApiEnvironmentDetailRespDTO.Processor>of()).stream().map(processor -> {
            ApiEnvironmentSaveReqDTO.Processor source = new ApiEnvironmentSaveReqDTO.Processor();
            source.setProcessorType(processor.getProcessorType());
            source.setName(processor.getName());
            source.setConfig(processor.getConfig());
            source.setSortOrder(processor.getSortOrder());
            source.setEnabled(processor.getEnabled());
            return source;
        }).toList());
        return normalize(reqDTO);
    }

    private void saveChildren(UUID environmentId, NormalizedAggregate aggregate) {
        aggregate.httpConfigs.forEach(row -> row.setEnvironmentId(environmentId));
        aggregate.variables.forEach(row -> row.setEnvironmentId(environmentId));
        aggregate.dataSources.forEach(row -> row.setEnvironmentId(environmentId));
        aggregate.processors.forEach(row -> row.setEnvironmentId(environmentId));

        if (!aggregate.httpConfigs.isEmpty()) {
            httpMapper.insertBatch(aggregate.httpConfigs);
        }
        if (!aggregate.variables.isEmpty()) {
            variableMapper.insertBatch(aggregate.variables);
        }
        if (!aggregate.dataSources.isEmpty()) {
            dataSourceMapper.insertBatch(aggregate.dataSources);
        }
        if (!aggregate.processors.isEmpty()) {
            processorMapper.insertBatch(aggregate.processors);
        }
    }

    /** 全量替换语义：逻辑删除旧子资源后整批写入新列表 */
    private void replaceChildren(UUID environmentId, NormalizedAggregate aggregate) {
        deleteChildren(environmentId);
        saveChildren(environmentId, aggregate);
    }

    private void deleteChildren(UUID environmentId) {
        httpMapper.deleteByEnvironmentId(environmentId);
        variableMapper.deleteByEnvironmentId(environmentId);
        dataSourceMapper.deleteByEnvironmentId(environmentId);
        processorMapper.deleteByEnvironmentId(environmentId);
    }

    /** 归一化后的聚合子资源实体集合 */
    private static class NormalizedAggregate {

        private final List<ApiEnvironmentHttp> httpConfigs = new ArrayList<>();
        private final List<ApiEnvironmentVariable> variables = new ArrayList<>();
        private final List<ApiDataSource> dataSources = new ArrayList<>();
        private final List<ApiEnvironmentProcessor> processors = new ArrayList<>();
    }
}
