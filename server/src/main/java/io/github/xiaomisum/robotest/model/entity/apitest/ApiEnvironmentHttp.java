package io.github.xiaomisum.robotest.model.entity.apitest;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import java.util.Map;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.List;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "api_environment_http", autoResultMap = true)
public class ApiEnvironmentHttp extends BaseUuidDO<ApiEnvironmentHttp> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID environmentId;
    private String name;
    /** 引用名称（对应 Ryze ref_name，步骤中引用该配置） */
    private String refName;
    private String baseUrl;
    private String defaultMethod;
    /** 默认请求头 [{key, value, enabled}] */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> defaultHeaders;
    private Integer timeoutMs;
    private Integer connectTimeoutMs;
    private Boolean followRedirects;
    private Boolean verifySsl;
    /** 是否为该环境的默认 HTTP 配置（每环境有且仅有一个） */
    private Boolean isDefault;
}
