package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_plan_execution_record")
public class TestPlanExecutionRecord extends BaseUuidDO<TestPlanExecutionRecord> {

    private UUID planId;
    private UUID snapshotNodeId;
    private UUID executorId;
    private String result;
    private String note;
    private LocalDateTime executedAt;
}
