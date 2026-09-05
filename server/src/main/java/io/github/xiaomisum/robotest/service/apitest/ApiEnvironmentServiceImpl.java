package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.audit.AuditOperation;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiDataSourceTestReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentCopyReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentSortReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentVariableCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiHttpConfigTestReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiDataSourceTestRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvImportResultRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentIdRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentListItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentSetDefaultRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentVariableRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiHttpTestRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironment;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.util.JsonUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_ENV_TASK_BOUND;

@Service
public class ApiEnvironmentServiceImpl implements ApiEnvironmentService {

    private static final String SCOPE_PROJECT = "project";
    /** 变量名仅允许字母/数字/下划线（详细设计 3.3.1） */
    private static final java.util.regex.Pattern VARIABLE_NAME_PATTERN = java.util.regex.Pattern.compile("^[A-Za-z0-9_]+$");
    /** 仅放行随服务打包的驱动，防止任意类加载（安全约束） */
    private static final Set<String> SUPPORTED_JDBC_DRIVERS = Set.of("org.postgresql.Driver", "com.mysql.cj.jdbc.Driver");
    private static final int JDBC_LOGIN_TIMEOUT_SECONDS = 10;
    /** 处理器类型常量 */
    private static final String PROC_TYPE_PRE = "preprocessor";
    private static final String PROC_TYPE_POST = "postprocessor";

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
        return environments.stream().map(env -> {
            ApiEnvironmentListItemRespDTO item = new ApiEnvironmentListItemRespDTO();
            item.setId(env.getId().toString());
            item.setName(env.getName());
            item.setDescription(env.getDescription());
            item.setIsDefault(env.getIsDefault());
            item.setSortOrder(env.getSortOrder());
            item.setHttpConfigCount(sizeOrZero(env.getHttpConfigs()));
            item.setVariableCount(sizeOrZero(env.getVariables()));
            item.setDataSourceCount(sizeOrZero(env.getDataSources()));
            item.setProcessorCount(sizeOrZero(env.getProcessors()));
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
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        if (environmentMapper.existsByProjectIdAndName(projectId, reqDTO.getName(), null)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_ENV_NAME_EXISTS);
        }
        NormalizedAggregate aggregate = normalize(reqDTO);

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
        applyAggregate(env, aggregate);
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
        NormalizedAggregate aggregate = normalize(reqDTO);

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
        applyAggregate(update, aggregate);
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
        ApiEnvironmentDetailRespDTO detail = assembleDetail(source);
        List<ApiEnvironment> siblings = environmentMapper.listByProject(projectId, null);
        int nextSort = siblings.stream().mapToInt(env -> env.getSortOrder() != null ? env.getSortOrder() : 0).max()
                .orElse(-1) + 1;

        ApiEnvironment copy = new ApiEnvironment();
        copy.setId(UUID.randomUUID());
        copy.setProjectId(projectId);
        copy.setName(reqDTO.getName());
        copy.setDescription(source.getDescription());
        copy.setScope(SCOPE_PROJECT);
        // 副本始终不抢占默认标记，避免复制操作改变执行默认行为
        copy.setIsDefault(false);
        copy.setSortOrder(nextSort);

        NormalizedAggregate aggregate = fromDetail(detail);
        applyAggregate(copy, aggregate);
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

    // ========== 变量（随环境聚合提交，3.3） ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditOperation(operation = "UPDATE", entityType = "ApiEnvironment")
    public ApiEnvironmentVariableRespDTO addVariableFromResult(UUID projectId, UUID workspaceId, UUID userId,
            UUID id, ApiEnvironmentVariableCreateReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiEnvironment env = requireEnv(projectId, id);
        String name = reqDTO.getName();
        if (name == null || !VARIABLE_NAME_PATTERN.matcher(name).matches()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        List<Map<String, Object>> variables = new ArrayList<>(copyList(env.getVariables()));
        boolean exists = variables.stream().anyMatch(v -> name.equals(v.get("name")));
        if (exists) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_ENV_VARIABLE_EXISTS);
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("value", reqDTO.getValue());
        row.put("description", reqDTO.getDescription());
        row.put("sourceStepId", parseUuidOrNull(reqDTO.getSourceStepId()));
        row.put("sourceReportId", parseUuidOrNull(reqDTO.getSourceReportId()));
        variables.add(row);

        // 仅更新 variables JSONB 列（C9 部分更新原则）
        ApiEnvironment update = new ApiEnvironment();
        update.setId(id);
        update.setVariables(variables);
        environmentMapper.updateById(update);
        return toVariableResp(name, row.get("value"), (String) row.get("description"), hasText((String) row.get("value")));
    }

    // ========== 连接测试（3.1.7 / 3.1.8，请求体传配置不落库） ==========

    @Override
    public ApiDataSourceTestRespDTO testDataSourceConfig(UUID projectId, UUID workspaceId, UUID userId,
            UUID id, ApiDataSourceTestReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireEnv(projectId, id);
        return testConnection(reqDTO.getUrl(), reqDTO.getDriver(), reqDTO.getConnectionProperties());
    }

    @Override
    public ApiHttpTestRespDTO testHttpConfig(UUID projectId, UUID workspaceId, UUID userId,
            UUID id, ApiHttpConfigTestReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireEnv(projectId, id);

        long start = System.currentTimeMillis();
        try {
            HttpResponse<Void> response = executeHttpGet(reqDTO.getBaseUrl());
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

    @Override
    public ApiEnvironmentDetailRespDTO exportEnvironment(UUID projectId, UUID workspaceId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiEnvironmentDetailRespDTO detail = assembleDetail(requireEnv(projectId, id));
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
            // 覆盖语义：保留原 id 与排序，仅替换内容与聚合资源
            ApiEnvironment update = new ApiEnvironment();
            update.setId(existing.getId());
            update.setDescription(payload.getDescription());
            applyAggregate(update, aggregate);
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
        env.setSortOrder(nextSort);
        applyAggregate(env, aggregate);
        environmentMapper.insert(env);
        return new ApiEnvImportResultRespDTO(1, 0, 0);
    }

    // ========== 连接测试内部实现 ==========

    /** 3.1.7 连接判定核心：Redis 按 URL 协议走 RESP，JDBC 仅放行内置驱动 */
    private ApiDataSourceTestRespDTO testConnection(String url, String driver,
            Map<String, Object> connectionProperties) {
        if (url != null && (url.startsWith("redis://") || url.startsWith("rediss://"))) {
            try {
                return openRedisConnection(url);
            } catch (Exception e) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.API_DATASOURCE_CONN_FAILED,
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        }
        if (!SUPPORTED_JDBC_DRIVERS.contains(driver)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_DATASOURCE_CONN_FAILED,
                    "不支持的数据库驱动：" + driver);
        }

        Properties props = new Properties();
        if (connectionProperties != null) {
            for (Map.Entry<String, Object> entry : connectionProperties.entrySet()) {
                if (entry.getValue() != null && entry.getKey() instanceof String keyName) {
                    props.put(keyName, String.valueOf(entry.getValue()));
                }
            }
        }
        try (Connection connection = openJdbcConnection(driver, url, props)) {
            DatabaseMetaData meta = connection.getMetaData();
            String version = meta.getDatabaseProductName() + " " + meta.getDatabaseMajorVersion()
                    + "." + meta.getDatabaseMinorVersion();
            return new ApiDataSourceTestRespDTO(true, "连接成功", version);
        } catch (Exception e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_DATASOURCE_CONN_FAILED,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    /** PING 建连超时，与 JDBC 登录超时口径一致 */
    private static final Duration REDIS_CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /** 可测接缝：真实 RESP 建连逻辑，单测中以 spy 覆盖 */
    protected ApiDataSourceTestRespDTO openRedisConnection(String url) throws Exception {
        RedisURI uri = RedisURI.create(url);
        uri.setTimeout(REDIS_CONNECT_TIMEOUT);
        RedisClient client = RedisClient.create(uri);
        StatefulRedisConnection<String, String> connection = client.connect();
        try {
            connection.sync().ping();
            String info = connection.sync().info("server");
            String redisVersion = null;
            if (info != null) {
                for (String line : info.split("\r?\n")) {
                    if (line.startsWith("redis_version:")) {
                        redisVersion = line.substring("redis_version:".length()).trim();
                        break;
                    }
                }
            }
            String version = redisVersion == null ? null : "Redis " + redisVersion;
            return new ApiDataSourceTestRespDTO(true, "连接成功", version);
        } finally {
            connection.close();
            client.shutdown();
        }
    }

    /** 可测接缝：真实 JDBC 建连逻辑，单测中以 spy 覆盖 */
    protected Connection openJdbcConnection(String driver, String url, Properties props) throws Exception {
        Class.forName(driver);
        DriverManager.setLoginTimeout(JDBC_LOGIN_TIMEOUT_SECONDS);
        return DriverManager.getConnection(url, props);
    }

    /** 可测接缝：真实 HTTP GET 逻辑，单测中以 spy 覆盖 */
    protected HttpResponse<Void> executeHttpGet(String baseUrl) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(10000))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl))
                .timeout(Duration.ofMillis(30000))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.discarding());
    }

    // ========== 环境导入导出 ==========

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

    private List<Map<String, Object>> copyList(List<Map<String, Object>> source) {
        if (source == null) {
            return List.of();
        }
        List<Map<String, Object>> copy = new ArrayList<>(source.size());
        for (Map<String, Object> item : source) {
            if (item == null) {
                continue;
            }
            copy.add(new LinkedHashMap<>(item));
        }
        return copy;
    }

    private ApiEnvironmentDetailRespDTO assembleDetail(ApiEnvironment env) {
        ApiEnvironmentDetailRespDTO detail = new ApiEnvironmentDetailRespDTO();
        detail.setId(env.getId().toString());
        detail.setName(env.getName());
        detail.setDescription(env.getDescription());
        detail.setScope(env.getScope());
        detail.setIsDefault(env.getIsDefault());
        detail.setSortOrder(env.getSortOrder());

        List<Map<String, Object>> httpConfigs = env.getHttpConfigs();
        if (httpConfigs == null || httpConfigs.isEmpty()) {
            detail.setHttpConfigs(List.of());
        } else {
            detail.setHttpConfigs(httpConfigs.stream().map(row -> {
                ApiEnvironmentDetailRespDTO.HttpConfig config = new ApiEnvironmentDetailRespDTO.HttpConfig();
                config.setName(str(row.get("name")));
                config.setRefName(str(row.get("refName")));
                config.setBaseUrl(str(row.get("baseUrl")));
                config.setIsDefault(bool(row.get("isDefault")));
                config.setHeaders(convertHeaders(asList(row.get("headers"))));
                return config;
            }).toList());
        }

        // 变量值明文展示，hasValue 标识是否已配置
        detail.setVariables(copyList(env.getVariables()).stream().map(row -> {
            ApiEnvironmentDetailRespDTO.Variable variable = new ApiEnvironmentDetailRespDTO.Variable();
            variable.setName(str(row.get("name")));
            String value = str(row.get("value"));
            boolean hasValue = value != null && !value.isEmpty();
            variable.setValue(hasValue ? value : null);
            variable.setHasValue(hasValue);
            variable.setDescription(str(row.get("description")));
            return variable;
        }).toList());

        detail.setDataSources(copyList(env.getDataSources()).stream().map(row -> {
            ApiEnvironmentDetailRespDTO.DataSource ds = new ApiEnvironmentDetailRespDTO.DataSource();
            ds.setName(str(row.get("name")));
            ds.setRefName(str(row.get("refName")));
            ds.setDriver(str(row.get("driver")));
            ds.setUrl(str(row.get("url")));
            ds.setConnectionProperties(asMap(row.get("connectionProperties")));
            Integer maxPoolSize = row.get("maxPoolSize") instanceof Number n ? n.intValue() : null;
            ds.setMaxPoolSize(maxPoolSize);
            ds.setIsDefault(bool(row.get("isDefault")));
            return ds;
        }).toList());

        detail.setProcessors(copyList(env.getProcessors()).stream().map(row -> {
            ApiEnvironmentDetailRespDTO.Processor processor = new ApiEnvironmentDetailRespDTO.Processor();
            processor.setProcessorType(str(row.get("processorType")));
            processor.setName(str(row.get("name")));
            processor.setConfig(asMap(row.get("config")));
            Integer sortOrder = row.get("sortOrder") instanceof Number n ? n.intValue() : null;
            processor.setSortOrder(sortOrder);
            processor.setEnabled(bool(row.get("enabled")));
            return processor;
        }).toList());
        return detail;
    }

    private List<ApiEnvironmentDetailRespDTO.HeaderItem> convertHeaders(List<?> rows) {
        if (rows == null) {
            return List.of();
        }
        return rows.stream().filter(row -> row instanceof Map).map(row -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) row;
            ApiEnvironmentDetailRespDTO.HeaderItem item = new ApiEnvironmentDetailRespDTO.HeaderItem();
            item.setKey(str(m.get("key")));
            item.setValue(str(m.get("value")));
            item.setEnabled(bool(m.get("enabled")));
            return item;
        }).toList();
    }

    /**
     * 归一化并校验聚合子资源：HTTP 配置缺省自动生成默认配置、ref_name 缺省按 http_N 生成、变量名校验
     */
    private NormalizedAggregate normalize(ApiEnvironmentSaveReqDTO reqDTO) {
        NormalizedAggregate aggregate = new NormalizedAggregate();

        List<ApiEnvironmentSaveReqDTO.HttpConfig> httpConfigs = reqDTO.getHttpConfigs();
        if (httpConfigs == null || httpConfigs.isEmpty()) {
            httpConfigs = List.of(defaultHttpConfig());
        }
        for (int i = 0; i < httpConfigs.size(); i++) {
            ApiEnvironmentSaveReqDTO.HttpConfig source = httpConfigs.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", source.getName());
            row.put("refName", source.getRefName() != null && !source.getRefName().isBlank()
                    ? source.getRefName() : "http_" + (i + 1));
            row.put("baseUrl", source.getBaseUrl() == null || source.getBaseUrl().isBlank()
                    ? "" : source.getBaseUrl());
            row.put("headers", normalizeHeaders(source.getHeaders()));
            aggregate.httpConfigs.add(row);
        }

        if (reqDTO.getVariables() != null) {
            aggregate.variables.addAll(normalizeVariables(reqDTO.getVariables()));
        }

        if (reqDTO.getDataSources() != null) {
            for (ApiEnvironmentSaveReqDTO.DataSource source : reqDTO.getDataSources()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", source.getName());
                row.put("refName", source.getRefName());
                row.put("driver", source.getDriver() == null ? "" : source.getDriver());
                row.put("url", source.getUrl());
                row.put("connectionProperties", source.getConnectionProperties() != null
                        ? new HashMap<>(source.getConnectionProperties()) : Map.of());
                row.put("maxPoolSize", source.getMaxPoolSize() != null ? source.getMaxPoolSize() : 5);
                aggregate.dataSources.add(row);
            }
        }

        if (reqDTO.getProcessors() != null) {
            for (ApiEnvironmentSaveReqDTO.Processor source : reqDTO.getProcessors()) {
                if (!PROC_TYPE_PRE.equals(source.getProcessorType())
                        && !PROC_TYPE_POST.equals(source.getProcessorType())) {
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("processorType", source.getProcessorType());
                row.put("name", source.getName());
                row.put("config", source.getConfig() != null ? new HashMap<>(source.getConfig()) : Map.of());
                row.put("sortOrder", source.getSortOrder() != null ? source.getSortOrder() : 0);
                row.put("enabled", !Boolean.FALSE.equals(source.getEnabled()));
                aggregate.processors.add(row);
            }
        }
        return aggregate;
    }

    /**
     * 归一化并校验变量列表（聚合保存与从结果添加共用）：名称仅字母/数字/下划线且同批唯一；取值明文存储
     */
    private List<Map<String, Object>> normalizeVariables(List<ApiEnvironmentSaveReqDTO.Variable> sources) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (ApiEnvironmentSaveReqDTO.Variable source : sources) {
            if (source.getName() == null || !VARIABLE_NAME_PATTERN.matcher(source.getName()).matches()) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
            }
            if (!names.add(source.getName())) {
                // 变量重名：同环境内变量名唯一
                throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", source.getName());
            row.put("value", source.getValue());
            row.put("description", source.getDescription());
            rows.add(row);
        }
        return rows;
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

    private ApiEnvironmentSaveReqDTO.HttpConfig defaultHttpConfig() {
        ApiEnvironmentSaveReqDTO.HttpConfig config = new ApiEnvironmentSaveReqDTO.HttpConfig();
        config.setName("默认配置");
        config.setRefName("default");
        config.setBaseUrl("http://localhost");
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
        reqDTO.setHttpConfigs((detail.getHttpConfigs() != null ? detail.getHttpConfigs() : List.<ApiEnvironmentDetailRespDTO.HttpConfig>of())
                .stream().map(config -> {
            ApiEnvironmentSaveReqDTO.HttpConfig source = new ApiEnvironmentSaveReqDTO.HttpConfig();
            source.setName(config.getName());
            source.setRefName(config.getRefName());
            source.setBaseUrl(config.getBaseUrl());
            source.setHeaders(config.getHeaders().stream().map(header -> {
                ApiEnvironmentSaveReqDTO.HeaderItem item = new ApiEnvironmentSaveReqDTO.HeaderItem();
                item.setKey(header.getKey());
                item.setValue(header.getValue());
                item.setEnabled(header.getEnabled());
                return item;
            }).toList());
            return source;
        }).toList());
        reqDTO.setVariables((detail.getVariables() != null ? detail.getVariables()
                : List.<ApiEnvironmentDetailRespDTO.Variable>of()).stream().map(variable -> {
            ApiEnvironmentSaveReqDTO.Variable source = new ApiEnvironmentSaveReqDTO.Variable();
            source.setName(variable.getName());
            source.setDescription(variable.getDescription());
            source.setValue(Boolean.TRUE.equals(variable.getHasValue()) ? variable.getValue() : null);
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

    /** 把归一化聚合写入环境实体的 JSONB 列（创建/更新/复制/导入共用） */
    private void applyAggregate(ApiEnvironment env, NormalizedAggregate aggregate) {
        env.setHttpConfigs(aggregate.httpConfigs);
        env.setVariables(aggregate.variables);
        env.setDataSources(aggregate.dataSources);
        env.setProcessors(aggregate.processors);
    }

    private Long sizeOrZero(List<?> list) {
        return (long) (list == null ? 0 : list.size());
    }

    private String str(Object value) {
        return value == null ? null : value.toString();
    }

    private Boolean bool(Object value) {
        return value instanceof Boolean b ? b : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private List<?> asList(Object value) {
        return value instanceof List ? (List<?>) value : List.of();
    }

    private ApiEnvironmentVariableRespDTO toVariableResp(String name, Object value, String description,
            boolean hasValue) {
        ApiEnvironmentVariableRespDTO resp = new ApiEnvironmentVariableRespDTO();
        resp.setName(name);
        resp.setValue(value == null ? null : value.toString());
        resp.setHasValue(hasValue);
        resp.setDescription(description);
        return resp;
    }

    /** 归一化后的聚合子资源（待写入 JSONB 列的 Map 列表） */
    private static class NormalizedAggregate {

        private final List<Map<String, Object>> httpConfigs = new ArrayList<>();
        private final List<Map<String, Object>> variables = new ArrayList<>();
        private final List<Map<String, Object>> dataSources = new ArrayList<>();
        private final List<Map<String, Object>> processors = new ArrayList<>();
    }
}
