package io.github.xiaomisum.robotest.model.entity.admin;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class SysPermission extends BaseUuidDO<SysPermission> {

    private String code;
    private String name;
    private String parentCode;
    private String module;
    private String scope;
    private Integer sortOrder;
}
