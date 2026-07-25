package io.github.xiaomisum.robotest.model.entity;

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
@TableName("test_plan_execution_record")
public class TestPlanExecutionRecord extends BaseUuidDO<TestPlanExecutionRecord> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID planId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID snapshotNodeId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID executorId;
    private String result;
    private String note;
    private LocalDateTime executedAt;
}
