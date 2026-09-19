package io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze;

import io.github.xiaomisum.robotest.service.apitest.execution.ports.EnvSnapshot;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.StepSpec;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.SuiteBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 场景套件构建（端口 {@link SuiteBuilder} 实现）：委托 {@link SceneRyzeConverter}，保持引擎转换口径单点。
 */
@Component
public class SceneSuiteBuilder implements SuiteBuilder {

    @Override
    public Map<String, Object> buildSuite(String title, EnvSnapshot env, Map<String, Object> suiteVariables,
            List<Map<String, Object>> perStepVariables, List<StepSpec> steps,
            List<Map<String, Object>> sceneProcessors) {
        return SceneRyzeConverter.buildSuite(title, env, suiteVariables, perStepVariables, steps, sceneProcessors);
    }

    @Override
    public Map<String, Object> buildSuiteVariables(EnvSnapshot env, List<Map<String, Object>> sceneVariables) {
        return SceneRyzeConverter.buildSuiteVariables(env, sceneVariables);
    }
}