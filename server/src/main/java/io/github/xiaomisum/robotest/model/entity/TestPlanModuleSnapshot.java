package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_plan_module_snapshot")
public class TestPlanModuleSnapshot extends BaseUuidDO<TestPlanModuleSnapshot> {

    private String planId;
    private String originalModuleId;
    private String parentId;
    private String name;
    private String type;
    private Integer sortOrder;
}
