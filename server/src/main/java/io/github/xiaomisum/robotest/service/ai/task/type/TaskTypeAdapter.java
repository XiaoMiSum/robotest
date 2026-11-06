package io.github.xiaomisum.robotest.service.ai.task.type;

import java.util.List;
import java.util.Map;

/**
 * 任务类型适配器 SPI（总册 3.6.1，替代旧 TaskHandler）：每个 type 注册 input 校验、
 * 工具白名单与预算默认、提示词与提案结构——执行核是通用循环，不含任何任务形态分支；
 * 实现随 S4 落地（13 个 type），本接口在 S2 先行定型。
 */
public interface TaskTypeAdapter {

    /** 任务类型（总册 3.6.1 全量枚举，注册表内唯一） */
    String type();

    /**
     * 提交期输入校验（总册 3.6.2：不合法抛 1000018115），执行期不再重复校验。
     */
    void validateInput(Map<String, Object> input);

    /** 该类型的工具白名单签发依据（tool_code 清单；与 ai_task_tool 启用行取交集后交模型声明） */
    List<String> toolCodes();

    /** 预算默认值 {maxSteps, maxDurationSeconds, maxTokens, maxCost}，签发时仅可下调（1000018126） */
    Map<String, Object> defaultBudget();

    /** 系统提示词（唯一登记点，提示词单轨化，S4 落地后删除 Handler.defaultPrompt 回落链） */
    String systemPrompt();

    /**
     * 执行终态产出：将模型最终结构化输出解析为提案草稿（空 = 只读结论类，任务直接 closed）。
     *
     * @param finalOutput 模型末跳正文
     * @param input       任务输入（回读定位用）
     */
    List<ProposalDraft> buildProposals(String finalOutput, Map<String, Object> input);
}
