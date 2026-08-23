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
@TableName(value = "api_debug_record", autoResultMap = true)
public class ApiDebugRecord extends BaseUuidDO<ApiDebugRecord> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID userId;
    private String name;
    private String protocol;
    /** HTTP 方法（jdbc 时为空） */
    private String method;
    private String url;
    /** 请求头 [{key, value, enabled}] */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> headers;
    /** 请求体类型：none / json / form / raw / binary */
    private String bodyType;
    /** 请求体内容（结构随 bodyType） */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> body;
    /** Query 参数 [{key, value, enabled}] */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> queryParams;
    /** JDBC 取样器配置（V1.2 暂不使用） */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> jdbcConfig;
    /** 前置/后置处理器列表 */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> processors;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID environmentId;
    private Integer timeoutMs;
    private LocalDateTime executedAt;
    private Integer durationMs;
    /** 执行结果：success / failed / error */
    private String status;
    private Integer responseStatus;
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> responseHeaders;
    /** 响应体（截断存储，最大 1MB） */
    private String responseBody;
    private Integer responseSize;
    private String errorMessage;
}
