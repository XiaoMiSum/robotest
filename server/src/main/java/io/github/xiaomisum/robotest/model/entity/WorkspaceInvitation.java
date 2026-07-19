package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseDO;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("workspace_invitation")
public class WorkspaceInvitation extends BaseDO {

    @TableId
    private String id;
    private String workspaceId;
    private String token;
    private String createdBy;
    private LocalDateTime expiresAt;
    private Integer maxUses;
    private Integer useCount;
    private String status;
}
