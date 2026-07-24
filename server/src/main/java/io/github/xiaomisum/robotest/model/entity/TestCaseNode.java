package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_case_node")
public class TestCaseNode extends BaseUuidDO<TestCaseNode> {

    private UUID documentId;
    private UUID parentId;
    private String type;
    private String title;
    private String priority;
    private Integer sortOrder;
    private Integer version;
}
