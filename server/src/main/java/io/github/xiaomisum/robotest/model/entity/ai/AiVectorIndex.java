package io.github.xiaomisum.robotest.model.entity.ai;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 向量索引（详设 2.6）：逻辑外键归属 project，检索先按项目过滤再做相似比对；
 * embedding 列为 pgvector 定长维度（schema 注释：n = ai_embedding_config.dimensions）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_vector_index")
public class AiVectorIndex extends BaseUuidDO<AiVectorIndex> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;

    /** 实体类型（requirement / testcase / bug，详设 4.4 写侧口径） */
    private String entityType;

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID entityId;

    private Integer chunkIndex;

    private String content;

    /**
     * pgvector 文本表示（形如 [0.1,0.2]）：仅经 mapper.insertBatch 的 CAST 写入；
     * 检索相似度由数据库侧计算，向量本身不读回应用层。
     */
    private String embedding;

    /** 嵌入版本标识（model:dims:operator），全量重建后随写入批次切换 */
    private String embeddingVersion;

    private LocalDateTime indexedAt;
}
