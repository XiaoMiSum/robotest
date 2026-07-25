package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

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
    private String description;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID reporterId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID assigneeId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID relatedCaseId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID relatedPlanId;
}
