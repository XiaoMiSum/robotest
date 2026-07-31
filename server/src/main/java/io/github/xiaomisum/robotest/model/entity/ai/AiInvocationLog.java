package io.github.xiaomisum.robotest.model.entity.ai;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.UUID;

/**
 * AI 调用审计（仅调用元数据，不存 Prompt 与生成内容）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_invocation_log")
public class AiInvocationLog extends BaseUuidDO<AiInvocationLog> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID userId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID workspaceId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    private String functionType;
    private String model;
    private Integer durationMs;
    private Integer promptTokens;
    private Integer completionTokens;
    private String status;
    private String errorCode;
}
