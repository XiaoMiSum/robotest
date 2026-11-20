package io.github.xiaomisum.robotest.model.entity.tcase;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.UUID;

/**
 * 用例节点关联需求（一条用例至多关联一条需求）：documentId 冗余所属文档，供计划/评审创建时按文档分组带入用例。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ft_case_requirement")
public class TestCaseRequirement extends BaseUuidDO<TestCaseRequirement> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID requirementId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID caseNodeId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID documentId;
}
