package io.github.xiaomisum.robotest.model.entity.bug;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bug")
public class Bug extends BaseUuidDO<Bug> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    private String title;
    private String severity;
    private String priority;
    private String status;
    private String bugType;
    // 重现步骤，Markdown 原文存储
    private String reproSteps;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID moduleId;
    private String keywords;
    private LocalDate dueDate;
    private Boolean confirmed;
    private Integer reopenCount;
    private LocalDateTime lastReopenedAt;
    private String resolution;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID duplicateOfBugId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID resolvedBy;
    private LocalDateTime resolvedAt;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID rejectedBy;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID closedBy;
    private LocalDateTime closedAt;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID reporterId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID assigneeId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID relatedCaseId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID relatedPlanId;
}
