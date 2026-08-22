package io.github.xiaomisum.robotest.model.entity.project;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("project_setting")
public class ProjectSetting extends BaseUuidDO<ProjectSetting> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    private String domain;
    private String settingKey;
    private String settingValue;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID updatedBy;
}
