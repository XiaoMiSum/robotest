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
 * 助手消息（总册 2.9）：content 流式结束后落盘供断线重连续读；intent 固化预览结构，execution 承载回执。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_assistant_message", autoResultMap = true)
public class AiAssistantMessage extends BaseUuidDO<AiAssistantMessage> {

    /** 所属会话（逻辑外键） */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID conversationId;

    /** user / assistant / system */
    private String role;

    /** 消息正文（Markdown） */
    private String content;

    /** 用户选中的上下文实体引用集合（数据引用，非 API 上下文） */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> attachments;

    /** 意图解析结构化预览（动作、目标、字段级变更、作用域与过期时间） */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> intent;

    /** 来源引用集合（只读问答与预览附带的可跳转引用） */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> citations;

    /** 执行回执 {status: previewed/executed/rejected, results[], …} */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> execution;

    /** streaming / done / interrupted / error */
    private String status;
}
