package io.github.xiaomisum.robotest.model.entity.apitest;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 定时任务（定时任务详细设计 2.1.1），测试计划（场景批量执行）与接口同步两类统一管理
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "api_scheduled_task", autoResultMap = true)
public class ApiScheduledTask extends BaseUuidDO<ApiScheduledTask> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    /** scene_execute / import_swagger */
    private String taskType;
    private String name;
    private String description;
    /** 历史遗留列（旧版绑定 Swagger URL 配置/场景 ID），新任务不再写入 */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID boundObjectId;
    private String boundObjectName;
    /** 执行方式（scene_execute 任务）：all / modules / scenes */
    private String executionScope;
    /** 指定模块（多选），execution_scope=modules 时必填 */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<UUID> moduleIds;
    /** 指定场景（多选），execution_scope=scenes 时必填 */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<UUID> sceneIds;
    /** OpenAPI/Swagger JSON 文件 URL（import_swagger 任务必填） */
    private String openapiUrl;
    /** 目标环境（scene_execute 任务必填） */
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
