package io.github.xiaomisum.robotest.model.entity.apitest;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.UUID;

/**
 * 接口级变量（接口管理详细设计 2.1.4），供场景步骤链接引用时自动导入
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("api_interface_variable")
public class ApiInterfaceVariable extends BaseUuidDO<ApiInterfaceVariable> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID interfaceId;
    /** ${name} 引用 */
    private String name;
    private String defaultValue;
    private String description;
    private Boolean required;
    private Integer sortOrder;
}
