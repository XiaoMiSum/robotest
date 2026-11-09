package io.github.xiaomisum.robotest.service.ai.task.type;

import io.github.xiaomisum.robotest.service.ai.task.TaskSubmitContext;
import io.github.xiaomisum.robotest.service.ai.task.core.TaskExecutionStrategy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import xyz.migoo.framework.common.util.JsonUtils;

/**
 * 任务类型适配器 SPI（总册 3.6.1，替代旧处理器双轨）：每个 type 注册 input 校验、
 * 工具白名单与预算默认、提示词与提案结构——执行核是通用循环，不含任何任务形态分支；
 * 实现随 S4 落地（13 个 type），本接口在 S2 先行定型。
 */
public interface TaskTypeAdapter {

    Map<String, Object> STANDARD_BUDGET = Map.of(
            "maxSteps", 40,
            "maxDurationSeconds", 600,
            "maxTokens", 200000,
            "maxCost", 5);

    record PreparationContext(UUID taskId, UUID userId, UUID workspaceId, UUID projectId,
            Map<String, Object> input, String feedback) {
    }

    /**
     * 执行策略登记；目标型任务默认使用 Agent，确定性或对话型任务可直接返回自己的策略 Bean。
     */
    default TaskExecutionStrategy executionStrategy(TaskExecutionStrategy defaultAgentStrategy) {
        return defaultAgentStrategy;
    }

    /** 任务类型（总册 3.6.1 全量枚举，注册表内唯一） */
    String type();

    /**
     * 提交期输入校验（总册 3.6.2：不合法抛 1000018115），执行期不再重复校验。
     */
    void validateInput(Map<String, Object> input);

    /**
     * 带提交上下文的输入校验（3.6.2 校验顺序「类型 → 输入」）：需要项目范围（归属、去重、状态）
     * 的类型重写本方法，默认回落无上下文实现（S4 各 TypeAdapter 落地）。
     */
    default void validateInput(Map<String, Object> input, TaskSubmitContext context) {
        validateInput(input);
    }

    /**
     * 本次任务输入的必需模型能力（总册 4.2 模型选型）：提交与执行两条路径据此选型；
     * 缺省无必需能力（纯文本调用）；需结合上下文派生的类型重写带 context 的重载。
     */
    default String requiredCapability(Map<String, Object> input) {
        return null;
    }

    /** 带上下文的能力派生（如批量随附截图需结合项目范围判定），默认回落无上下文实现 */
    default String requiredCapability(Map<String, Object> input, TaskSubmitContext context) {
        return requiredCapability(input);
    }

    /**
     * 文档级元数据（总册 2.6.1 result_meta）：执行成功时随任务落库（如 requirement_import 的
     * documentMeta），供确认面板预填；缺省无（null）。
     */
    default Map<String, Object> buildResultMeta(String finalOutput, Map<String, Object> input) {
        return null;
    }

    /** 该类型的工具白名单签发依据（tool_code 清单；与 ai_task_tool 启用行取交集后交模型声明） */
    List<String> toolCodes();

    /** 预算默认值 {maxSteps, maxDurationSeconds, maxTokens, maxCost}，签发时仅可下调（1000018126） */
    Map<String, Object> defaultBudget();

    /** 系统提示词（唯一登记点，提示词单轨化，S4 落地后删除 Handler.defaultPrompt 回落链） */
    String systemPrompt();

    /**
     * 构造执行快照；实现可读取业务数据并绑定上下文感知解析器，但禁止调用模型或写业务数据。
     */
    default AgentExecutionPreparation prepare(PreparationContext context) {
        Map<String, Object> input = context.input() == null ? Map.of() : context.input();
        return new AgentExecutionPreparation(Map.of(), JsonUtils.toJsonString(input), List.of(),
                output -> buildProposals(output, input), output -> buildResultMeta(output, input));
    }

    /**
     * 执行终态产出：将模型最终结构化输出解析为提案草稿（空 = 只读结论类，任务直接 closed）。
     *
     * @param finalOutput 模型末跳正文
     * @param input       任务输入（回读定位用）
     */
    List<ProposalDraft> buildProposals(String finalOutput, Map<String, Object> input);
}
