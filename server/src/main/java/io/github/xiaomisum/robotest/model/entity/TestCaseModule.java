package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_case_module")
public class TestCaseModule extends BaseUuidDO<TestCaseModule> {

    private UUID projectId;
    private UUID parentId;
    private String type;
    private String name;
    private Integer sortOrder;
}
