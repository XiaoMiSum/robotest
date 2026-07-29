package io.github.xiaomisum.robotest.model.entity.plan;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_plan_node_snapshot")
public class TestPlanNodeSnapshot extends BaseUuidDO<TestPlanNodeSnapshot> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID planId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID originalNodeId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID documentSnapshotId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID parentId;
    private String title;
    private String type;
    private String priority;
    private Boolean isAssociated;
    private String lastResult;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID lastExecutorId;
    private LocalDateTime lastExecutedAt;
    private Integer sortOrder;
}
