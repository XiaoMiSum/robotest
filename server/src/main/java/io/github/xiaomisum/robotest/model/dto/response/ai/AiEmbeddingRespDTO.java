package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 向量 API 配置（GET /api/ai/embedding，详设 3.4；单例，密钥不回显）。
 */
@Data
public class AiEmbeddingRespDTO {

    private String provider;

    private String baseUrl;

    private String embeddingModel;

    private Integer dimensions;

    private String operator;

    private String indexType;

    private Boolean enabled;

    private Boolean keyConfigured;

    /** 历史版本记录：[{ version, embeddingModel, dimensions, operator, retiredAt }] */
    private List<Map<String, Object>> versions;

    /** 存在未消费的维度 / 算子变更（versions 非空）→ 前端引导全量重建 */
    private Boolean requiresReindex;

    /** 最近连通性测试（无记录为 null） */
    private AiModelRespDTO.LastTest lastTest;
}
