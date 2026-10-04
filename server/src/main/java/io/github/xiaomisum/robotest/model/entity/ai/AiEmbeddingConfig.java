package io.github.xiaomisum.robotest.model.entity.ai;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 向量 API 配置（单例行，详设 2.4）：维度 / 算子变更时旧配置追加进 versions，
 * 重建完成前检索不可用（1000018122）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_embedding_config", autoResultMap = true)
public class AiEmbeddingConfig extends BaseUuidDO<AiEmbeddingConfig> {

    /** 供应商类别，取值同模型配置 provider */
    private String provider;

    /** 向量端点（独立于生成模型） */
    private String baseUrl;

    /** 密钥密文（AES；接口不回显，仅支持替换） */
    private String apiKeyEncrypted;

    private String embeddingModel;

    /** 向量维度（决定 ai_vector_index.embedding 列维度） */
    private Integer dimensions;

    /** 距离算子：cosine / l2 / inner_product */
    private String operator;

    /** 索引类型：hnsw / ivfflat */
    private String indexType;

    /** 启停；未启用则 RAG 与相似检测不可用 */
    private Boolean enabled;

    /** 历史版本记录 [{ version, embeddingModel, dimensions, operator, retiredAt }]；非空即需重建 */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> versions;

    private LocalDateTime lastTestAt;

    /** 最近连通性测试结果 { success, latencyMs, dimensions, msg } */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> lastTestResult;
}
