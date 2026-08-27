package io.github.xiaomisum.robotest.model.entity.apitest;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Swagger URL 配置（定时任务详细设计 2.1.3），定时导入任务的绑定对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "api_swagger_url", autoResultMap = true)
public class ApiSwaggerUrl extends BaseUuidDO<ApiSwaggerUrl> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    private String name;
    private String url;
    /** swagger / openapi */
    private String format;
    private String lastImportStatus;
    private LocalDateTime lastImportAt;

}
