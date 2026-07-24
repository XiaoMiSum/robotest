package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_plan")
public class TestPlan extends BaseUuidDO<TestPlan> {

    private UUID projectId;
    private String name;
    private String description;
    private String status;
    private UUID executorId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String environment;
}
