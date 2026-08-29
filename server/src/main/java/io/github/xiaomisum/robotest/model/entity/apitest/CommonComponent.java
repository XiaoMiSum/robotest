package io.github.xiaomisum.robotest.model.entity.apitest;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("api_component")
public class CommonComponent extends BaseUuidDO<CommonComponent> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID workspaceId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    private String scope;
    private String type;
    private String name;
    private String description;
    /** 组件排序号（仅前置/后置处理器类使用，场景引入时决定处理器执行顺序） */
    private Integer sortOrder;
    private Boolean enabled;
    private String config;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID updatedBy;
}
