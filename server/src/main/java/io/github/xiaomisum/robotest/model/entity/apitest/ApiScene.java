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
    /** 优先级：P0/P1/P2/P3，NULL=未设置 */
    private String priority;
    /** 状态：draft（草稿）/ published（已发布），缺省 draft（测试场景详细设计 2.1.2） */
    private String status;
    /** [{name, value, description}]，场景级变量唯一权威源，随场景整体读写 */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> variables;
    /** 场景级处理器，结构与 Ryze 元件一致 */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> processors;
    /** 步骤聚合（JSONB）：结构与前端步骤对象一致，每步含 variables 数组（合并自原 api_scene_step_variable） */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> steps;
    /** 每次保存递增，乐观锁依据 */
    private Integer changeVersion;

}
