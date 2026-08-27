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
 * 定时任务（定时任务详细设计 2.1.1），接口导入与场景执行两类统一管理
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "api_scheduled_task", autoResultMap = true)
public class ApiScheduledTask extends BaseUuidDO<ApiScheduledTask> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    /** import_swagger / scene_execute */
    private String taskType;
    private String name;
    private String description;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID boundObjectId;
    private String boundObjectName;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID environmentId;
    /** 5 位 Cron，分钟精度 */
    private String cronExpression;
    private Boolean enabled;
    /** success / failed / running */
    private String lastExecutionStatus;
    private LocalDateTime lastExecutionAt;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID createdBy;

}
