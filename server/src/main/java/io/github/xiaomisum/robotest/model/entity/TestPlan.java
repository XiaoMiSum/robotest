package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseDO;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_plan")
public class TestPlan extends BaseDO {

    @TableId
    private String id;
    private String projectId;
    private String name;
    private String description;
    private String status;
    private String executorId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String environment;
}
