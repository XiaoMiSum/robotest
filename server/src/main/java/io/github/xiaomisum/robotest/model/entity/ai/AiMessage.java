package io.github.xiaomisum.robotest.model.entity.ai;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 全局智能助手消息（详细设计 2.1.2）。
 *
 * <p>tool_calls 为 assistant 消息发起的工具调用载荷（name/arguments/callId 数组），
 * tool 消息通过 toolCallId 与所属调用衔接，保证会话历史回填 LLM 时序列完整。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_message", autoResultMap = true)
public class AiMessage extends BaseUuidDO<AiMessage> {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_TOOL = "tool";

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID conversationId;
    private String role;
    private String content;
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> toolCalls;
    private String toolCallId;
}
