package io.github.xiaomisum.robotest.model.entity.apitest;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 执行记录（API测试基础设施详细设计 2.1.3）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "api_execution_record", autoResultMap = true)
public class ApiExecutionRecord extends BaseUuidDO<ApiExecutionRecord> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID sceneId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID environmentId;
    /** platform / pipeline */
    private String executionMode;
    /** pending → running → success / failed / error / cancelled / timeout */
    private String status;
    /** manual / scheduled / pipeline */
    private String triggerType;
    /** 执行完成后写入关联报告 ID */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID reportId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID repositoryId;
    private String pipelineId;
    private String pipelineUrl;
    private String errorMessage;
    private LocalDateTime executedAt;
    private Integer durationMs;

}
