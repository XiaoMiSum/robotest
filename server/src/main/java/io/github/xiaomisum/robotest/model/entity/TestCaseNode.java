package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseDO;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_case_node")
public class TestCaseNode extends BaseDO {

    @TableId
    private String id;
    private String documentId;
    private String parentId;
    private String type;
    private String title;
    private String priority;
    private Integer sortOrder;
    private Integer version;
}
