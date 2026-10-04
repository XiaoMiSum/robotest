package io.github.xiaomisum.robotest.model.entity.ai;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.UUID;

/**
 * 用量明细（详设 2.8）：每次模型 / 向量调用（成功与失败）各记一条，任务级汇总同步到 ai_task。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_usage_log")
public class AiUsageLog extends BaseUuidDO<AiUsageLog> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;

    /** 助手交互等无任务调用为 null */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID taskId;

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID userId;

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID modelId;

    private String promptScene;

    /** 调用类型：chat / embedding */
    private String callType;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    /** 调用端到端耗时 */
    private Integer latencyMs;

    /** success / failed（详设 2.8） */
    private String status;

    private Integer errorCode;

    /** 本次调用成本（详设 4.3：按调用模型当时单价计，未配置单价恒 0） */
    private java.math.BigDecimal cost;
}
