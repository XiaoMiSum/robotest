package io.github.xiaomisum.robotest.service.ai.vector;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.service.ai.task.core.TaskExecutionStrategy;
import io.github.xiaomisum.robotest.service.ai.task.type.ProposalDraft;
import io.github.xiaomisum.robotest.service.ai.task.type.TaskTypeAdapter;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

/**
 * vector_reindex 类型适配器（总册 3.6.1，4.4）：scope 输入校验与首版预算登记；
 * 确定性执行走 {@link VectorReindexExecutionStrategy}（不由模型决定是否执行），
 * 豁免 chat 模型选型（总册 4.2），无提示词正文与工具白名单，不产生提案。
 */
@Component
public class VectorReindexTypeAdapter implements TaskTypeAdapter {

    /** 首版预算（总册 3.6.1）：steps 按 embedding 批次与索引替换动作计，不按实体逐条计 */
    private static final Map<String, Object> REINDEX_BUDGET = Map.of(
            "maxSteps", 500,
            "maxDurationSeconds", 3600,
            "maxTokens", 10000000,
            "maxCost", 20);

    @Resource
    private VectorReindexExecutionStrategy executionStrategy;

    @Override
    public String type() {
        return Constants.AiTaskType.VECTOR_REINDEX;
    }

    @Override
    public void validateInput(Map<String, Object> input) {
        resolveTypes(input);
    }

    @Override
    public TaskExecutionStrategy executionStrategy(TaskExecutionStrategy defaultAgentStrategy) {
        return executionStrategy;
    }

    @Override
    public List<String> toolCodes() {
        return List.of();
    }

    @Override
    public Map<String, Object> defaultBudget() {
        return REINDEX_BUDGET;
    }

    @Override
    public String systemPrompt() {
        // 确定性执行不经模型，无场景提示词正文（配置中心亦未登记该 scene）
        return "";
    }

    @Override
    public List<ProposalDraft> buildProposals(String finalOutput, Map<String, Object> input) {
        return List.of();
    }

    /**
     * 解析详设 3.6.1 的 scope：缺省 / 未带 entityTypes = 全量；
     * entityTypes 为白名单数组，非法取值报 1000018115（提交与执行两条路径共用）。
     */
    static Set<String> resolveTypes(Map<String, Object> input) {
        Object scope = input == null ? null : input.get("scope");
        if (scope == null) {
            return new LinkedHashSet<>(VectorIndexService.ALL_TYPES);
        }
        if (!(scope instanceof Map<?, ?> scopeMap)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(),
                    "scope 须为对象 { entityTypes: [...] }");
        }
        Object raw = scopeMap.get("entityTypes");
        if (raw == null) {
            return new LinkedHashSet<>(VectorIndexService.ALL_TYPES);
        }
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(),
                    "scope.entityTypes 缺省全量重建，显式传值须为非空数组");
        }
        Set<String> types = new LinkedHashSet<>();
        for (Object item : list) {
            if (!(item instanceof String entity) || !VectorIndexService.ALL_TYPES.contains(entity)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(),
                        "scope.entityTypes 仅支持 requirement / testcase / bug");
            }
            types.add(entity);
        }
        return types;
    }
}
