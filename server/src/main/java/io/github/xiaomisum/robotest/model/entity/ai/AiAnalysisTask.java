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
 * AI 异步分析任务（状态机与结果快照）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_analysis_task", autoResultMap = true)
public class AiAnalysisTask extends BaseUuidDO<AiAnalysisTask> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID workspaceId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    private String type;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID targetId;
    private String status;
    private Integer progress;
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> result;
    private String errorMessage;
    private String executorInstance;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID createdBy;
}
