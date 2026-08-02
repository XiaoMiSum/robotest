package io.github.xiaomisum.robotest.model.entity.ai;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.UUID;

/**
 * 用例语义向量（与 test_case_node 中 type=case 的节点一对一；embedding 以向量文本形式存取）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("case_embedding")
public class CaseEmbedding extends BaseUuidDO<CaseEmbedding> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID nodeId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    private String embedding;
    private String sourceHash;
    private String model;
}
