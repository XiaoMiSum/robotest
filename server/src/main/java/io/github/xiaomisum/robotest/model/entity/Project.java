package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("project")
public class Project extends BaseUuidDO<Project> {

    private String workspaceId;
    private String name;
    private String description;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String createdBy;
}
