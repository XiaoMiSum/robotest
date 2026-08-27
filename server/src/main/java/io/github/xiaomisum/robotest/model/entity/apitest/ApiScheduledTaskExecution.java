package io.github.xiaomisum.robotest.model.entity.apitest;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 定时任务执行记录（定时任务详细设计 2.1.2），每次定时/手动触发留痕
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "api_scheduled_task_execution", autoResultMap = true)
public class ApiScheduledTaskExecution extends BaseUuidDO<ApiScheduledTaskExecution> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID taskId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    /** scheduled / manual */
    private String triggerType;
    /** success / failed / skipped */
    private String status;
    private String errorMessage;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID reportId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID importRecordId;
    private LocalDateTime triggeredAt;
    private Integer durationMs;

}
