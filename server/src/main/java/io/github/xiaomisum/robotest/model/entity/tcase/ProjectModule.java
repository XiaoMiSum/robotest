package io.github.xiaomisum.robotest.model.entity.tcase;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("project_module")
public class ProjectModule extends BaseUuidDO<ProjectModule> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID parentId;
    private String name;
    /** 源侧映射标识：包容器存完整包名、类模块存测试类全限定名（GitLab 集成详设 3.5） */
    private String sourceId;
    private Integer sortOrder;
}