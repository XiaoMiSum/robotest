package io.github.xiaomisum.robotest.model.entity.ai;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 全局智能助手会话（归属用户 + 工作空间，内容仅本人可见，详细设计 2.1.1）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_conversation")
public class AiConversation extends BaseUuidDO<AiConversation> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID userId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID workspaceId;
    private String title;
    private LocalDateTime lastActiveAt;
}
