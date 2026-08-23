package io.github.xiaomisum.robotest.model.entity.apitest;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.Map;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "api_environment_processor", autoResultMap = true)
public class ApiEnvironmentProcessor extends BaseUuidDO<ApiEnvironmentProcessor> {

    public static final String TYPE_PRE = "preprocessor";
    public static final String TYPE_POST = "postprocessor";

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID environmentId;
    /** 处理器类型：preprocessor / postprocessor */
    private String processorType;
    private String name;
    /** 处理器配置 JSON（与 Ryze 处理器元件结构一致） */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> config;
    private Integer sortOrder;
    private Boolean enabled;
}
