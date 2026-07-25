package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_case_module")
public class TestCaseModule extends BaseUuidDO<TestCaseModule> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID parentId;
    private String type;
    private String name;
    private Integer sortOrder;
}
