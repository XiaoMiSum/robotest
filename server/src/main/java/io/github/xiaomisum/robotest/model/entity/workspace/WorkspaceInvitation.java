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
@TableName("ws_invitation")
public class WorkspaceInvitation extends BaseUuidDO<WorkspaceInvitation> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID workspaceId;
    private String token;
    private String createdBy;
    private LocalDateTime expiresAt;
    private Integer maxUses;
    private Integer useCount;
    private String status;
}
