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
@TableName("workspace_user")
public class WorkspaceUser extends BaseUuidDO<WorkspaceUser> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID userId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID workspaceId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID workspaceRole;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID defaultProjectId;
    private LocalDateTime joinedAt;
}
