package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_plan_execution_record")
public class TestPlanExecutionRecord extends BaseUuidDO<TestPlanExecutionRecord> {

    private String planId;
    private String snapshotNodeId;
    private String executorId;
    private String result;
    private String note;
    private LocalDateTime executedAt;
}
