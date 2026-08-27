package io.github.xiaomisum.robotest.model.entity.apitest;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.UUID;

/**
 * 步骤级变量（测试场景详细设计 2.1.4）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "api_scene_step_variable", autoResultMap = true)
public class ApiSceneStepVariable extends BaseUuidDO<ApiSceneStepVariable> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID stepId;
    /** ${name} 引用 */
    private String name;
    private String value;
    /** custom=手动创建 / interface=从接口导入 */
    private String source;
    /** 来源接口变量 ID，source=interface 时有效 */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID interfaceVariableId;
    private String description;
    private Integer sortOrder;

}
