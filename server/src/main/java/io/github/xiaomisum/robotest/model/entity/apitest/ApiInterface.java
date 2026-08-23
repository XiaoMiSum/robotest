package io.github.xiaomisum.robotest.model.entity.apitest;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 接口定义（接口管理详细设计 2.1.2）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "api_interface", autoResultMap = true)
public class ApiInterface extends BaseUuidDO<ApiInterface> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    /** 空为未分组 */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID moduleId;
    private String name;
    /** V1.2 仅 http，jdbc 随场景模块开放 */
    private String protocol;
    private String method;
    private String path;
    private String description;
    /** [{key, value, enabled}] */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> headers;
    private String bodyType;
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> body;
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> queryParams;
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> restParams;
    /** {type, username, password}，存储加密 */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> auth;
    private String status;
    /** 「我创建的」视图过滤依据 */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID createdBy;
    /** 每次保存递增（乐观锁） */
    private Integer changeVersion;
    /** {status, headers, body} */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> responseExample;
    /** 场景/Mock 引用计数，>0 时禁止删除 */
    private Integer referenceCount;
}
