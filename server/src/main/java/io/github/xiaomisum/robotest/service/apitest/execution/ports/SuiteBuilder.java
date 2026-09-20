package io.github.xiaomisum.robotest.service.apitest.execution.ports;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 平台场景模型 → 引擎可执行套件 Map（防腐缝端口，04 §3.1.1）。引擎转换细节在
 * {@code adapters/ryze/SceneSuiteBuilder}（委托 SceneRyzeConverter）。
 */
public interface SuiteBuilder {

    Map<String, Object> buildSuite(String title, EnvSnapshot env, Map<String, Object> suiteVariables,
            List<Map<String, Object>> perStepVariables, List<StepSpec> steps,
            List<Map<String, Object>> sceneProcessors);

    Map<String, Object> buildSuiteVariables(EnvSnapshot env, List<Map<String, Object>> sceneVariables);

    /**
     * 组装场景子 TestSuite（定时任务多场景组合执行）：variables 仅场景自身变量，
     * 子 suite 携带 metadata {sceneId, taskId} 供结果树反查（定时任务详细设计 4.3）。
     */
    Map<String, Object> buildSceneSuite(String title, EnvSnapshot env, Map<String, Object> suiteVariables,
            List<Map<String, Object>> perStepVariables, List<StepSpec> steps,
            List<Map<String, Object>> sceneProcessors, UUID sceneId, UUID taskId);

    /** 仅场景自身变量的 suite 级变量（环境变量由调用方在顶层大 suite 挂载，子 suite 经 context chain 继承） */
    Map<String, Object> buildSceneVariables(List<Map<String, Object>> sceneVariables);

    /** 环境配置元件（http 配置 + 数据源），供顶层大 suite 统一挂载 */
    List<Map<String, Object>> buildConfigureElements(EnvSnapshot env);
}