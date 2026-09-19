package io.github.xiaomisum.robotest.service.apitest.execution.ports;

import java.util.List;
import java.util.Map;

/**
 * 步骤规格（端口自定模型，零 ryze）：步骤 → 引擎转换的中间契约，已解析的请求配置与校验/提取规则。
 */
public record StepSpec(String title, Map<String, Object> requestConfig,
        List<Map<String, Object>> validators,
        List<Map<String, Object>> extractors) {
}