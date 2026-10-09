package io.github.xiaomisum.robotest.model.entity.tcase;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.List;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "test_case_node", autoResultMap = true)
public class TestCaseNode extends BaseUuidDO<TestCaseNode> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID documentId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID parentId;
    private String type;
    private String title;
    private String priority;
    /** 用例标签（jsonb 数组）；辅助功能采纳落库，脑图渲染与向量索引均不使用 */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<String> tags;
    private Integer sortOrder;
    private Integer version;
    private Boolean aiGenerated;
}
