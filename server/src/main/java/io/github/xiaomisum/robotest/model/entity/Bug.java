package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseDO;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bug")
public class Bug extends BaseDO {

    @TableId
    private String id;
    private String projectId;
    private String title;
    private String severity;
    private String priority;
    private String status;
    private String description;
    private String reporterId;
    private String assigneeId;
    private String relatedCaseId;
    private String relatedPlanId;
}
