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
 * 助手消息（总册 2.9）：content 流式结束后落盘供断线重连续读；intent / execution 为历史列，保留以兼容存量数据。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_assistant_message", autoResultMap = true)
public class AiAssistantMessage extends BaseUuidDO<AiAssistantMessage> {

    /** 所属会话（逻辑外键） */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID conversationId;

    /** user / assistant / system / tool */
    private String role;

    /** 消息正文（Markdown） */
    private String content;

    /** 用户选中的上下文实体引用集合（数据引用，非 API 上下文） */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> attachments;

    /** 用户随消息上传的图片文件引用集合 [{fileId, name}]（仅存引用不存字节，vision 调用的媒体来源） */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> images;

    /** assistant 消息的工具调用载荷（名称与入参），role = tool 结果消息经 toolCallId 与之配对 */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> toolCalls;

    /** role = tool 结果消息回指的调用标识（详设 2 / 总册 2.9） */
    private String toolCallId;

    /** 历史意图列（保留以兼容存量数据，不再产生新数据） */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> intent;

    /** 来源引用集合（只读问答附带的可跳转引用） */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> citations;

    /** 历史执行回执列（保留以兼容存量数据，不再产生新数据） */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> execution;

    /** streaming / done / interrupted / error */
    private String status;
}
