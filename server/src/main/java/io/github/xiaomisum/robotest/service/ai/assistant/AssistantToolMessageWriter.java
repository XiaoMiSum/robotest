package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantMessage;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantMessageMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * tool 消息回填写入（详设 4.1 查询类能力）：插入 role=tool 结果消息，供后续消息的多轮上下文配对。
 *
 * <p>从 AssistantService 下沉为独立组件：任务执行层（{@code AssistantParseHandler} /
 * {@code AssistantToolLoop}）依赖本组件而非会话服务，消除「会话服务 → 任务引擎 → handler →
 * 会话服务」的循环依赖。</p>
 */
@Component
public class AssistantToolMessageWriter {

    @Resource
    private AiAssistantMessageMapper messageMapper;

    /** 会话归属与空间在任务提交期已校验（详设 4.1），此处不重复 */
    public void appendToolMessage(UUID conversationId, String toolCallId, String content) {
        AiAssistantMessage message = new AiAssistantMessage();
        message.setConversationId(conversationId);
        message.setRole(Constants.AiAssistantMessageRole.TOOL);
        message.setToolCallId(toolCallId);
        message.setContent(content);
        message.setStatus(Constants.AiAssistantMessageStatus.DONE);
        messageMapper.insert(message);
    }
}
