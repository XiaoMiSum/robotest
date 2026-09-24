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
@TableName("ws_project_activity")
public class ProjectActivity extends BaseUuidDO<ProjectActivity> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID actorId;

    private String actorName;
    private String resourceType;

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID resourceId;

    private String resourceName;
    private String action;
    private String summary;
    private LocalDateTime occurredAt;
}
