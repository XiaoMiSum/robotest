package io.github.xiaomisum.robotest.model.entity.admin;

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
@TableName(value = "sys_user_role", autoResultMap = true)
public class SysUserRole extends BaseUuidDO<SysUserRole> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID userId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID roleId;
    private LocalDateTime assignedAt;
}
