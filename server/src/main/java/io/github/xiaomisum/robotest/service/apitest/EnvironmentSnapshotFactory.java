package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironment;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironmentHttp;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironmentProcessor;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironmentVariable;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentHttpMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentProcessorMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentVariableMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 环境快照装配：调试与场景执行共用同一解析口径
 * （默认 HTTP 配置、变量明文、启用的前后置处理器）。
 */
@Slf4j
@Component
public class EnvironmentSnapshotFactory {

    private static final String TYPE_SENSITIVE = "sensitive";

    @Resource
    private ApiEnvironmentMapper environmentMapper;
    @Resource
    private ApiEnvironmentHttpMapper environmentHttpMapper;
    @Resource
    private ApiEnvironmentVariableMapper environmentVariableMapper;
    @Resource
    private ApiEnvironmentProcessorMapper environmentProcessorMapper;

    @Value("${robotest.env.secret-key:}")
    private String secretKeyBase64;

    /** 指定环境不可用时回退项目默认环境，均缺失时返回空快照 */
    public DebugRyzeConverter.EnvSnapshot resolve(UUID projectId, UUID environmentId) {
        ApiEnvironment env = environmentId != null
                ? environmentMapper.selectById(environmentId)
                : findDefaultEnvironment(projectId);
        if (env == null || !env.getProjectId().equals(projectId)) {
            return DebugRyzeConverter.EnvSnapshot.empty();
        }
        ApiEnvironmentHttp defaultHttp = environmentHttpMapper.listByEnvironmentId(env.getId()).stream()
                .filter(http -> Boolean.TRUE.equals(http.getIsDefault()))
                .findFirst()
                .orElse(null);
        Map<String, Object> variables = new LinkedHashMap<>();
        for (ApiEnvironmentVariable variable : environmentVariableMapper.listByEnvironmentId(env.getId())) {
            variables.put(variable.getName(), plaintext(variable));
        }
        List<Map<String, Object>> pre = processorConfigs(env.getId(), "preprocessor");
        List<Map<String, Object>> post = processorConfigs(env.getId(), "postprocessor");

        Map<String, Object> envHeaders = new LinkedHashMap<>();
        if (defaultHttp != null && defaultHttp.getDefaultHeaders() != null) {
            for (Map<String, Object> entry : defaultHttp.getDefaultHeaders()) {
                Object key = entry.get("key");
                if (key != null && !Boolean.FALSE.equals(entry.get("enabled"))) {
                    envHeaders.put(key.toString(), entry.getOrDefault("value", ""));
                }
            }
        }
        return new DebugRyzeConverter.EnvSnapshot(
                defaultHttp == null ? "" : Objects.requireNonNullElse(defaultHttp.getBaseUrl(), ""),
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

    private List<Map<String, Object>> processorConfigs(UUID envId, String processorType) {
        List<Map<String, Object>> configs = new java.util.ArrayList<>();
        for (ApiEnvironmentProcessor processor
                : environmentProcessorMapper.listByEnvironmentIdAndType(envId, processorType)) {
            if (Boolean.FALSE.equals(processor.getEnabled())) {
                continue;
            }
            configs.add(processor.getConfig());
        }
        return configs;
    }

    /** 变量明文：敏感变量解密后参与执行（执行需要真实值，区别于前端展示掩码） */
    private String plaintext(ApiEnvironmentVariable variable) {
        String value = variable.getValue();
        if (value == null || !TYPE_SENSITIVE.equals(variable.getType())) {
            return value;
        }
        try {
            byte[] key = SecretCryptoUtil.parseKey(secretKeyBase64);
            return key == null ? value : SecretCryptoUtil.decrypt(key, value);
        } catch (Exception ex) {
            log.warn("[api-env] 敏感变量 {} 解密失败，按密文参与执行", variable.getName());
            return value;
        }
    }
}
