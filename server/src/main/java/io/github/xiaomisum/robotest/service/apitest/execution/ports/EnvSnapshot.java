package io.github.xiaomisum.robotest.service.apitest.execution.ports;

import java.util.List;
import java.util.Map;

/**
 * 环境生效快照（端口自定模型，零 ryze）：调试/场景执行共用解析口径（基础设施详细设计 4.1.2）。
 * http 配置与数据源原样透传（转换器转为 suite configelements）、变量明文、全局前置/后置处理器。
 */
public record EnvSnapshot(String name,
        Map<String, Object> variables,
        List<Map<String, Object>> preprocessors,
        List<Map<String, Object>> postprocessors,
        List<Map<String, Object>> httpConfigs,
        List<Map<String, Object>> dataSources) {

    public static EnvSnapshot empty() {
        return new EnvSnapshot(null, Map.of(), List.of(), List.of(), List.of(), List.of());
    }
}