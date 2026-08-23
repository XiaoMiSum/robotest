package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.audit.AuditOperation;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentCopyReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentSortReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentIdRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentListItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentSetDefaultRespDTO;
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
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        NormalizedAggregate aggregate = normalize(reqDTO);

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

        byte[] cipherKey = null;
        Set<String> variableNames = Set.of();
        if (reqDTO.getVariables() != null) {
            variableNames = new HashSet<>();
            for (ApiEnvironmentSaveReqDTO.Variable source : reqDTO.getVariables()) {
                String type = source.getType() == null || source.getType().isBlank() ? TYPE_TEXT : source.getType();
                if (!VARIABLE_TYPES.contains(type)) {
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
                }
                if (!variableNames.add(source.getName())) {
                    // 变量重名：同环境内变量名唯一
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
                }
                if (TYPE_NUMBER.equals(type) && source.getValue() != null && !source.getValue().isBlank()) {
                    try {
                        new BigDecimal(source.getValue());
                    } catch (NumberFormatException e) {
                        throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
                    }
                }
                ApiEnvironmentVariable row = new ApiEnvironmentVariable();
                row.setName(source.getName());
                row.setDescription(source.getDescription());
                row.setType(type);
                String value = source.getValue();
                if (value != null && !value.isEmpty()) {
                    if (TYPE_SENSITIVE.equals(type)) {
                        // 密钥缺失属服务端配置缺陷，非业务异常，快速失败避免明文落库
                        if (cipherKey == null) {
                            cipherKey = SecretCryptoUtil.parseKey(secretKeyBase64);
                        }
                        if (cipherKey == null) {
                            throw new IllegalStateException(
                                    "环境敏感值加密密钥未配置（robotest.env.secret-key）");
                        }
                        value = SecretCryptoUtil.encrypt(cipherKey, value);
                    }
                    row.setValue(value);
                }
                aggregate.variables.add(row);
            }
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

    /** 复制场景：从源环境详情反向构建归一化聚合；敏感值已在详情层掩码化，此处还原为「未配置」 */
    private NormalizedAggregate fromDetail(ApiEnvironmentDetailRespDTO detail) {
        ApiEnvironmentSaveReqDTO reqDTO = new ApiEnvironmentSaveReqDTO();
        reqDTO.setHttpConfigs(detail.getHttpConfigs().stream().map(config -> {
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
        reqDTO.setVariables(detail.getVariables().stream().map(variable -> {
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
        reqDTO.setProcessors(detail.getProcessors().stream().map(processor -> {
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
