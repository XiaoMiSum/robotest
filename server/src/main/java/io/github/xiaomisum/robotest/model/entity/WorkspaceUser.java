package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("workspace_user")
public class WorkspaceUser extends BaseUuidDO<WorkspaceUser> {

    private String userId;
    private String workspaceId;
    private String workspaceRole;
    private String defaultProjectId;
    private LocalDateTime joinedAt;
}
