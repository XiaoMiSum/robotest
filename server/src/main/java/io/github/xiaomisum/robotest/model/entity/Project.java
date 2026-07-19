package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseDO;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("project")
public class Project extends BaseDO {

    @TableId
    private String id;
    private String workspaceId;
    private String name;
    private String description;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String createdBy;
}
