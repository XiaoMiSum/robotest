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

/**
 * Mock 访问日志（Mock服务详细设计 2.1.2），免登录命中审计
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "api_mock_access_log", autoResultMap = true)
public class ApiMockAccessLog extends BaseUuidDO<ApiMockAccessLog> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID mockId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    private String method;
    private String path;
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> requestHeaders;
    /** 截断存储 */
    private String requestBody;
    private Integer responseStatus;
    /** 截断存储 */
    private String responseBody;
    private Integer durationMs;
    private String clientIp;

}
