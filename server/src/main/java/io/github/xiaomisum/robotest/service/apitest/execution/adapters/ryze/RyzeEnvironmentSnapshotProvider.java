package io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironment;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentMapper;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.EnvSnapshot;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.EnvironmentSnapshotProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 环境快照装配（端口 {@link EnvironmentSnapshotProvider} 实现）：调试与场景执行共用同一解析口径。
 *
 * <p>环境聚合存储于主表 api_environment 的 JSONB 列（详细设计《环境管理详细设计说明书》），
 * 快照装配 http 配置/数据源整表与变量明文、启用的前后置处理器，由两个转换器据此生成 suite configelements。</p>
 */
@Component
public class RyzeEnvironmentSnapshotProvider implements EnvironmentSnapshotProvider {

    @Resource
    private ApiEnvironmentMapper environmentMapper;

    /** 指定环境不可用时回退项目默认环境，均缺失时返回空快照 */
    @Override
    public EnvSnapshot resolve(UUID projectId, UUID environmentId) {
        ApiEnvironment env = environmentId != null
                ? environmentMapper.selectById(environmentId)
                : findDefaultEnvironment(projectId);
        if (env == null || !env.getProjectId().equals(projectId)) {
            return EnvSnapshot.empty();
        }

        Map<String, Object> variables = new LinkedHashMap<>();
        for (Map<String, Object> row : nullToEmpty(env.getVariables())) {
            Object name = row.get("name");
            if (name != null) {
                variables.put(name.toString(), row.get("value"));
            }
        }

        List<Map<String, Object>> pre = processorConfigs(env.getProcessors(), "preprocessor");
        List<Map<String, Object>> post = processorConfigs(env.getProcessors(), "postprocessor");

        // http 配置/数据源整表透传，由转换器装配 configelements（基设详设 4.1.2）
        return new EnvSnapshot(
                env.getName(), variables, pre, post,
                nullToEmpty(env.getHttpConfigs()),
                nullToEmpty(env.getDataSources()));
    }

    private ApiEnvironment findDefaultEnvironment(UUID projectId) {
        return environmentMapper.findDefaultByProjectId(projectId);
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
                configs.add(normalizeProcessorElement(castMap(map)));
            }
        }
        return configs;
    }

    /**
     * 将平台存储的处理器元素标准化为 Ryze 引擎可识别的格式。
     * <p>平台存储结构（web 侧 toProcessorElement 编译产物）：{testclass, config, extractors, enabled, sortOrder}。
     * Ryze 引擎仅识别 {testclass, config, extractors}，需剥离平台 overlay 字段并将提取器由平台格式
     * ({source, expression, variableName}) 转为 Ryze 格式 ({testclass, field, ref_name})。</p>
     */
    public static Map<String, Object> normalizeProcessorElement(Map<String, Object> element) {
        Map<String, Object> result = new LinkedHashMap<>(element);
        // 剥离平台 overlay 字段（Ryze 引擎不识别）
        result.remove("enabled");
        result.remove("sortOrder");
        // 将提取器由平台存储格式转为 Ryze 元件格式
        if (result.get("extractors") instanceof List<?> rawList) {
            List<Map<String, Object>> source = SceneRyzeConverter.toStringKeyMapList(rawList);
            // 过滤无效提取器（空来源/禁用项），避免转换期抛出异常
            List<Map<String, Object>> valid = source.stream()
                    .filter(e -> {
                        Object s = e.get("source");
                        return s != null && !s.toString().isBlank()
                                && !Boolean.FALSE.equals(e.get("enabled"));
                    })
                    .toList();
            List<Map<String, Object>> converted = SceneRyzeConverter.convertExtractors(valid);
            if (converted.isEmpty()) {
                result.remove("extractors");
            } else {
                result.put("extractors", converted);
            }
        }
        return result;
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}