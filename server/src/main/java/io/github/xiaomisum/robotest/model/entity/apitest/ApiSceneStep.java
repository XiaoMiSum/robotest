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
 * 场景步骤（测试场景详细设计 2.1.3）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "api_scene_step", autoResultMap = true)
public class ApiSceneStep extends BaseUuidDO<ApiSceneStep> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID sceneId;
    private String name;
    /** http / jdbc（V1.2 仅 http 可执行） */
    private String stepType;
    private Integer sortOrder;
    private Boolean enabled;
    /** system/custom/public_step/copy/link，NULL 视同 custom */
    private String sourceType;
    /** 来源对象 ID：接口定义 ID 或公共步骤 ID */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID sourceId;
    /** 链接源被删除后降级展示与执行的快照 */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> sourceSnapshot;
    /** 来源接口定义 ID，卡片来源 tag 与跳转用（公共步骤的 source_id 为公共步骤 ID） */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID sourceInterfaceId;
    /** 来源接口名称冗余，避免查询 JOIN */
    private String sourceInterfaceName;
    /** {method, url, headers[], params[], body{type, content}, timeout} */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> requestConfig;
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> processors;
    /** [{id, name, enabled, target, condition, expected, expression}] */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> validators;
    /** [{id, name, enabled, source, expression, variableName}] */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> extractors;

}
