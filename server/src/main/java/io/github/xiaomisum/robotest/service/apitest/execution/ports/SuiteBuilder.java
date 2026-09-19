package io.github.xiaomisum.robotest.service.apitest.execution.ports;

import java.util.List;
import java.util.Map;

/**
 * 平台场景模型 → 引擎可执行套件 Map（防腐缝端口，04 §3.1.1）。引擎转换细节在
 * {@code adapters/ryze/SceneSuiteBuilder}（委托 SceneRyzeConverter）。
 */
public interface SuiteBuilder {

    Map<String, Object> buildSuite(String title, EnvSnapshot env, Map<String, Object> suiteVariables,
            List<Map<String, Object>> perStepVariables, List<StepSpec> steps,
            List<Map<String, Object>> sceneProcessors);

    Map<String, Object> buildSuiteVariables(EnvSnapshot env, List<Map<String, Object>> sceneVariables);
}