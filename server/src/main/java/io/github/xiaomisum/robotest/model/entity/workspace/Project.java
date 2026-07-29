package io.github.xiaomisum.robotest.model.entity.workspace;

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
@TableName("project")
public class Project extends BaseUuidDO<Project> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID workspaceId;
    private String name;
    private String description;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String createdBy;
}
