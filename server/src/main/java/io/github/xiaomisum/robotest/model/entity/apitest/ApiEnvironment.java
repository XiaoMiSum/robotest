package io.github.xiaomisum.robotest.model.entity.apitest;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("api_environment")
public class ApiEnvironment extends BaseUuidDO<ApiEnvironment> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    private String name;
    private String description;
    /** 环境归属范围：project（V1.2 全部）/ global（全局级预留扩展） */
    private String scope;
    private Boolean isDefault;
    private Integer sortOrder;
}
