package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentListItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentVariableRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironment;
import org.springframework.web.multipart.MultipartFile;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.util.JsonUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 环境有效快照装配器（接口测试域重构方案 04 §4.2）：环境聚合 JSONB 的归一化/反向转换/详情组装纯函数，
 * 零 IO 零状态（C2：装配与归一化不触达数据源）。
 */
public final class EnvironmentEffectiveSnapshot {

    private EnvironmentEffectiveSnapshot() {
    }

    /** 变量名仅允许字母/数字/下划线（详细设计 3.3.1） */
    public static final java.util.regex.Pattern VARIABLE_NAME_PATTERN =
            java.util.regex.Pattern.compile("^[A-Za-z0-9_]+$");
    /** 处理器类型常量 */
    private static final String PROC_TYPE_PRE = "preprocessor";
    private static final String PROC_TYPE_POST = "postprocessor";

    // ========== 列表/详情装配 ==========

    public static ApiEnvironmentListItemRespDTO toListItem(ApiEnvironment env) {
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
    }

    public static ApiEnvironmentDetailRespDTO assembleDetail(ApiEnvironment env) {
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

    public static List<ApiEnvironmentDetailRespDTO.HeaderItem> convertHeaders(List<?> rows) {
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

    // ========== 归一化与反向转换 ==========

    /**
     * 归一化并校验聚合子资源：HTTP 配置缺省自动生成默认配置、ref_name 缺省按 http_N 生成、变量名校验
     */
    public static NormalizedAggregate normalize(ApiEnvironmentSaveReqDTO reqDTO) {
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
            row.put("isDefault", Boolean.TRUE.equals(source.getIsDefault()));
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
                row.put("isDefault", Boolean.TRUE.equals(source.getIsDefault()));
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

    /** 复制/导入：从环境详情反向构建归一化聚合；敏感值已在详情层掩码化，此处还原为「未配置」 */
    public static NormalizedAggregate fromDetail(ApiEnvironmentDetailRespDTO detail) {
        ApiEnvironmentSaveReqDTO reqDTO = new ApiEnvironmentSaveReqDTO();
        reqDTO.setHttpConfigs((detail.getHttpConfigs() != null ? detail.getHttpConfigs() : List.<ApiEnvironmentDetailRespDTO.HttpConfig>of())
                .stream().map(config -> {
            ApiEnvironmentSaveReqDTO.HttpConfig source = new ApiEnvironmentSaveReqDTO.HttpConfig();
            source.setName(config.getName());
            source.setRefName(config.getRefName());
            source.setBaseUrl(config.getBaseUrl());
            source.setIsDefault(config.getIsDefault());
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
    public static void applyAggregate(ApiEnvironment env, NormalizedAggregate aggregate) {
        env.setHttpConfigs(aggregate.httpConfigs);
        env.setVariables(aggregate.variables);
        env.setDataSources(aggregate.dataSources);
        env.setProcessors(aggregate.processors);
    }

    // ========== 变量与字段工具 ==========

    /**
     * 归一化并校验变量列表（聚合保存与从结果添加共用）：名称仅字母/数字/下划线且同批唯一；取值明文存储
     */
    public static List<Map<String, Object>> normalizeVariables(List<ApiEnvironmentSaveReqDTO.Variable> sources) {
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

    /** 从结果添加变量：构造单条变量行（含来源步骤/报告溯源，详细设计 3.3.2） */
    public static Map<String, Object> variableRow(String name, Object value, String description,
            String sourceStepId, String sourceReportId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("value", value);
        row.put("description", description);
        row.put("sourceStepId", parseUuidOrNull(sourceStepId));
        row.put("sourceReportId", parseUuidOrNull(sourceReportId));
        return row;
    }

    public static ApiEnvironmentVariableRespDTO toVariableResp(String name, Object value, String description,
            boolean hasValue) {
        ApiEnvironmentVariableRespDTO resp = new ApiEnvironmentVariableRespDTO();
        resp.setName(name);
        resp.setValue(value == null ? null : value.toString());
        resp.setHasValue(hasValue);
        resp.setDescription(description);
        return resp;
    }

    public static ApiEnvironmentSaveReqDTO.HttpConfig defaultHttpConfig() {
        ApiEnvironmentSaveReqDTO.HttpConfig config = new ApiEnvironmentSaveReqDTO.HttpConfig();
        config.setName("默认配置");
        config.setRefName("default");
        config.setBaseUrl("http://localhost");
        // 缺省生成的配置即默认配置，保证引用未指定时仍可预选
        config.setIsDefault(true);
        return config;
    }

    public static List<Map<String, Object>> normalizeHeaders(List<ApiEnvironmentSaveReqDTO.HeaderItem> headers) {
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

    // ========== 通用取值工具 ==========

    public static List<Map<String, Object>> copyList(List<Map<String, Object>> source) {
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

    public static boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }

    public static UUID parseUuidOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Long sizeOrZero(List<?> list) {
        return (long) (list == null ? 0 : list.size());
    }

    public static String str(Object value) {
        return value == null ? null : value.toString();
    }

    public static Boolean bool(Object value) {
        return value instanceof Boolean b ? b : null;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    public static List<?> asList(Object value) {
        return value instanceof List ? (List<?>) value : List.of();
    }

    /** 归一化后的聚合子资源（待写入 JSONB 列的 Map 列表） */
    public static class NormalizedAggregate {

        private final List<Map<String, Object>> httpConfigs = new ArrayList<>();
        private final List<Map<String, Object>> variables = new ArrayList<>();
        private final List<Map<String, Object>> dataSources = new ArrayList<>();
        private final List<Map<String, Object>> processors = new ArrayList<>();
    }

    // ========== 环境导入导出 ==========

    /** 导入文件解析为环境详情载荷：非法 JSON/空值统一降级为参数校验失败（详细设计 3.2） */
    public static ApiEnvironmentDetailRespDTO parseImportPayload(MultipartFile file) {
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
}