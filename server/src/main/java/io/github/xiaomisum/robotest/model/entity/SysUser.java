package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseUuidDO<SysUser> {

    private String username;
    private String email;
    private String passwordHash;
    private String avatarUrl;
    private String status;
    private String lastActiveWorkspaceId;
}
