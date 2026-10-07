package io.github.xiaomisum.robotest.model.entity.ai;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.Map;
import java.util.UUID;

/**
 * 助手会话（总册 2.9）：归属人是唯一隔离维度，列表、可见性与权限校验只看 user_id，管理员同样不越权。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_assistant_conversation", autoResultMap = true)
public class AiAssistantConversation extends BaseUuidDO<AiAssistantConversation> {

    /** 会话标题（首问回填，可重命名，≤100 字符） */
    private String title;

    /** 归属人 = 唯一隔离维度 */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID userId;

    /** active / archived（归档后不可发消息） */
    private String status;

    /** 创建时上下文快照 {workspaceId, context, capturedAt}：默认上下文与来源展示，不参与权限判定 */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> contextSnapshot;
}
