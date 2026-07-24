package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("workspace_user")
public class WorkspaceUser extends BaseUuidDO<WorkspaceUser> {

    private UUID userId;
    private UUID workspaceId;
    private UUID workspaceRole;
    private UUID defaultProjectId;
    private LocalDateTime joinedAt;
}
