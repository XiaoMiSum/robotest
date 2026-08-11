package io.github.xiaomisum.robotest.model.entity.requirement;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.UUID;

/**
 * 文档-需求关联（脑图文档 ⇄ 需求池条目，US-AI-004）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("requirement_document_rel")
public class DocumentRequirementRel extends BaseUuidDO<DocumentRequirementRel> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID documentId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID requirementId;
}
