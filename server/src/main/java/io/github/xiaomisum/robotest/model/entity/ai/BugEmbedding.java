package io.github.xiaomisum.robotest.model.entity.ai;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.UUID;

/**
 * 缺陷语义向量（与 bug 一对一；embedding 以向量文本形式存取，SQL 侧经 ::vector 转换）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_bug_embedding")
public class BugEmbedding extends BaseUuidDO<BugEmbedding> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID bugId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    private String embedding;
    private String sourceHash;
    private String model;
}
