package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseDO;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseDO {

    @TableId
    private String id;
    private String name;
    private String description;
    private String type;
    private Boolean isSystem;
    private String permissions;
}
