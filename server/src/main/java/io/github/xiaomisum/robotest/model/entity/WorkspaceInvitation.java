package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("workspace_invitation")
public class WorkspaceInvitation extends BaseUuidDO<WorkspaceInvitation> {

    private String workspaceId;
    private String token;
    private String createdBy;
    private LocalDateTime expiresAt;
    private Integer maxUses;
    private Integer useCount;
    private String status;
}
