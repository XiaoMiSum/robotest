package io.github.xiaomisum.robotest.model.entity.apitest;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.UUID;

/**
 * 场景变量（测试场景详细设计 2.1.5）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "api_scenario_variable", autoResultMap = true)
public class ApiScenarioVariable extends BaseUuidDO<ApiScenarioVariable> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID sceneId;
    /** ${name} 引用 */
    private String name;
    private String value;
    private String description;
    private Integer sortOrder;

}
