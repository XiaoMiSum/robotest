package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bug")
public class Bug extends BaseUuidDO<Bug> {

    private UUID projectId;
    private String title;
    private String severity;
    private String priority;
    private String status;
    private String description;
    private UUID reporterId;
    private UUID assigneeId;
    private UUID relatedCaseId;
    private UUID relatedPlanId;
}
