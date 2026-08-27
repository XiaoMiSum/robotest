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
 * 测试场景（测试场景详细设计 2.1.2）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "api_scene", autoResultMap = true)
public class ApiScene extends BaseUuidDO<ApiScene> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    /** NULL=未分组 */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID moduleId;
    private String name;
    private String description;
    /** 默认执行环境 */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID environmentId;
    /** [{name, value, description}]，列表页冗余快照，权威数据在 api_scenario_variable */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> variables;
    /** 场景级处理器，结构与 Ryze 元件一致 */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> processors;
    /** all=任一步骤失败即停止 / continue=忽略错误继续 */
    private String failureRule;
    /** {sharedEnabled, items:[{id, key, value, enabled, domain}]} */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> cookieConfig;
    /** 每次保存递增，乐观锁依据 */
    private Integer changeVersion;

}
