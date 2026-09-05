package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironment;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 环境快照装配：调试与场景执行共用同一解析口径。
 *
 * <p>环境聚合存储于主表 api_environment 的 JSONB 列（详细设计《环境管理详细设计说明书》），
 * 快照直接解析第一条 HTTP 配置、变量明文、启用的前后置处理器。</p>
 */
@Component
public class EnvironmentSnapshotFactory {

    @Resource
    private ApiEnvironmentMapper environmentMapper;

    /** 默认 HTTP 配置：取 JSONB http_configs 首条 */
    private static final Map<String, Object> NO_HTTP = Map.of();

    /** 指定环境不可用时回退项目默认环境，均缺失时返回空快照 */
    public DebugRyzeConverter.EnvSnapshot resolve(UUID projectId, UUID environmentId) {
        ApiEnvironment env = environmentId != null
                ? environmentMapper.selectById(environmentId)
                : findDefaultEnvironment(projectId);
        if (env == null || !env.getProjectId().equals(projectId)) {
            return DebugRyzeConverter.EnvSnapshot.empty();
        }
        Map<String, Object> defaultHttp = first(env.getHttpConfigs());

        Map<String, Object> variables = new LinkedHashMap<>();
        for (Map<String, Object> row : nullToEmpty(env.getVariables())) {
            Object name = row.get("name");
            if (name != null) {
                variables.put(name.toString(), row.get("value"));
            }
        }

        List<Map<String, Object>> pre = processorConfigs(env.getProcessors(), "preprocessor");
        List<Map<String, Object>> post = processorConfigs(env.getProcessors(), "postprocessor");

        Map<String, Object> envHeaders = new LinkedHashMap<>();
        Object headers = defaultHttp.get("headers");
        if (headers instanceof List<?> rawHeaders) {
            for (Object entryObj : rawHeaders) {
                if (!(entryObj instanceof Map<?, ?> entry)) {
                    continue;
                }
                Object key = entry.get("key");
                if (key != null && !Boolean.FALSE.equals(entry.get("enabled"))) {
                    Object value = entry.get("value");
                    envHeaders.put(key.toString(), value == null ? "" : value);
                }
            }
        }
        return new DebugRyzeConverter.EnvSnapshot(
                Objects.requireNonNullElse(defaultHttp.get("baseUrl"), "").toString(),
                envHeaders, variables, pre, post);
    }

    private ApiEnvironment findDefaultEnvironment(UUID projectId) {
        return environmentMapper.selectList(
                        new LambdaQueryWrapperX<ApiEnvironment>()
                                .eq(ApiEnvironment::getProjectId, projectId)
                                .eq(ApiEnvironment::getIsDefault, true))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private List<Map<String, Object>> processorConfigs(List<Map<String, Object>> processors, String processorType) {
        List<Map<String, Object>> configs = new ArrayList<>();
        for (Map<String, Object> row : nullToEmpty(processors)) {
            if (!processorType.equals(row.get("processorType"))) {
                continue;
            }
            if (Boolean.FALSE.equals(row.get("enabled"))) {
                continue;
            }
            Object config = row.get("config");
            if (config instanceof Map<?, ?> map) {
                configs.add(castMap(map));
            }
        }
        return configs;
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static Map<String, Object> first(List<Map<String, Object>> rows) {
        List<Map<String, Object>> safe = nullToEmpty(rows);
        return safe.isEmpty() ? NO_HTTP : safe.get(0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
