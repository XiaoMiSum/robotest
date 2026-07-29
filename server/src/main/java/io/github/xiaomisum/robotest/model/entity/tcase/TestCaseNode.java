package io.github.xiaomisum.robotest.model.entity.tcase;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_case_node")
public class TestCaseNode extends BaseUuidDO<TestCaseNode> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID documentId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID parentId;
    private String type;
    private String title;
    private String priority;
    private Integer sortOrder;
    private Integer version;
}
