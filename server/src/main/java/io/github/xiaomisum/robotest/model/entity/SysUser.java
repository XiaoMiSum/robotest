package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseDO;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseDO {

    @TableId
    private String id;
    private String username;
    private String email;
    private String passwordHash;
    private String avatarUrl;
    private String status;
}
