package io.github.xiaomisum.robotest.model.entity.apitest;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "api_mock_definition", autoResultMap = true)
public class ApiMockDefinition extends BaseUuidDO<ApiMockDefinition> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID interfaceId;
    private String name;
    private String description;
    private String method;
    private String path;
    private Integer priority;
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> matchRules;
    private Boolean enabled;
    private Boolean followApi;
    private Integer responseStatus;
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> responseHeaders;
    private String responseBodyType;
    private String responseBody;
    private Integer delayMs;
    private Long hitCount;
    private LocalDateTime lastHitAt;
}
