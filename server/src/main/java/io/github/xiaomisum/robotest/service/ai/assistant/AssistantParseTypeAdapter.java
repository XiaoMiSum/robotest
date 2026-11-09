package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantConversation;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantMessage;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantConversationMapper;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantMessageMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiSettingsReader;
import io.github.xiaomisum.robotest.service.ai.task.TaskSubmitContext;
import io.github.xiaomisum.robotest.service.ai.task.core.TaskExecutionStrategy;
import io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport;
import io.github.xiaomisum.robotest.service.ai.task.type.ProposalDraft;
import io.github.xiaomisum.robotest.service.ai.task.type.TaskTypeAdapter;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

/**
 * assistant_parse 类型适配器（总册 3.6.1，分册 04 §4.1）：登记输入校验、vision 能力派生、
 * 助手预算与场景提示词正文；执行走对话式 {@link AssistantExecutionStrategy}（不套 Agent 循环），
 * 助手澄清 / 回答 / 变更预览按分册 04 处理，不产生通用提案。
 */
@Component
public class AssistantParseTypeAdapter implements TaskTypeAdapter {

    /** 助手首版预算（总册 3.6.1）：steps 与单消息 5 跳上限一致 */
    private static final Map<String, Object> ASSISTANT_BUDGET = Map.of(
            "maxSteps", 5,
            "maxDurationSeconds", 600,
            "maxTokens", 200000,
            "maxCost", 5);

    /**
     * agent 版内置默认（详设 4.1：工具使用规则 + 多轮上下文，候选清单改由工具按需查询）；
     * 场景主体可经配置中心自定义覆盖（scene = assistant_parse），本方法为唯一回落正文。
     */
    private static final String DEFAULT_PROMPT = """
            你是测试平台的智能助手，通过调用工具完成查询、澄清与数据变更任务：
            1. 需要项目、文档树、用例、评审、计划、进度或知识信息时先调用对应查询工具，
               一切 ID 只能取工具返回值，不得虚构；
            2. 信息不足、存在歧义或多个候选需要用户抉择时调用 ask_user 澄清（选项来自查询结果
               或上下文，2~5 个）；无据可依时明确说明，不得编造；
            3. 数据变更一律通过变更类工具发起：调用只生成变更预览，落库由用户确认后执行；
               变更参数须满足工具描述中的必填项，字段变更必须基于用户指令，不得臆造；
            4. 最终答复直接输出正文（Markdown，简洁，给出具体对象名称与数值），不复述工具调用过程。

            会话历史（时间正序，最后一条为用户最新输入）：
            {{history}}

            当前上下文（页面引用与会话项目状态）：
            {{context}}
            """;

    @Resource
    private AiAssistantMessageMapper messageMapper;
    @Resource
    private AiAssistantConversationMapper conversationMapper;
    @Resource
    private AssistantPromptSupport promptSupport;
    @Resource
    private AssistantExecutionStrategy executionStrategy;

    @Override
    public String type() {
        return Constants.AiTaskType.ASSISTANT_PARSE;
    }

    @Override
    public void validateInput(Map<String, Object> input) {
        requireMessageId(input);
    }

    @Override
    public void validateInput(Map<String, Object> input, TaskSubmitContext context) {
        UUID messageId = requireMessageId(input);
        // 桥接键只允许本人会话内的 assistant 消息（任务中心直提同码拒绝，防越权解析他人消息）
        if (context == null || context.userId() == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        AiAssistantMessage message = messageMapper.selectById(messageId);
        AiAssistantConversation conversation = message == null ? null
                : conversationMapper.selectById(message.getConversationId());
        if (message == null || !Constants.AiAssistantMessageRole.ASSISTANT.equals(message.getRole())
                || conversation == null || !context.userId().equals(conversation.getUserId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
    }

    /** 提交与执行两条路径的能力派生（详设 4.1）：当前消息附图非空 ⇒ vision；派生不抛错中断任务 */
    @Override
    public String requiredCapability(Map<String, Object> input) {
        AiAssistantMessage message = bridgeMessage(input);
        if (message == null) {
            return null;
        }
        return promptSupport.history(message.getConversationId(), message.getId()).stream()
                .filter(item -> Constants.AiAssistantMessageRole.USER.equals(item.getRole()))
                .reduce((first, second) -> second)
                .filter(item -> item.getImages() != null && !item.getImages().isEmpty())
                .map(item -> AiSettingsReader.CAPABILITY_VISION)
                .orElse(null);
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
        return ASSISTANT_BUDGET;
    }

    @Override
    public String systemPrompt() {
        return DEFAULT_PROMPT;
    }

    @Override
    public List<ProposalDraft> buildProposals(String finalOutput, Map<String, Object> input) {
        return List.of();
    }

    /** 执行期从任务输入取 messageId（提交期已校验，损坏数据按输入非法终止） */
    static UUID requireMessageId(Map<String, Object> input) {
        String value = ModelOutputSupport.trimToNull(
                ModelOutputSupport.asString(input == null ? null : input.get("messageId")));
        if (value == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
    }

    /** 能力派生桥接（详设 4.1）：非法输入静默返回 null，交由提交与执行期校验兜底 */
    private AiAssistantMessage bridgeMessage(Map<String, Object> input) {
        String value = ModelOutputSupport.trimToNull(
                ModelOutputSupport.asString(input == null ? null : input.get("messageId")));
        if (value == null) {
            return null;
        }
        try {
            return messageMapper.selectById(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
