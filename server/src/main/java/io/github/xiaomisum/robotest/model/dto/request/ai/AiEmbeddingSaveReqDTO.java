package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 保存向量 API 配置（PUT /api/ai/embedding，详设 3.4；单例，部分更新）。
 */
@Data
public class AiEmbeddingSaveReqDTO {

    private String provider;

    @Size(max = 500, message = "端点地址长度不能超过 500")
    private String baseUrl;

    /** 密钥明文；null 表示不替换 */
    @Size(max = 500, message = "密钥长度不能超过 500")
    private String apiKey;

    @Size(max = 100, message = "嵌入模型长度不能超过 100")
    private String embeddingModel;

    /** 向量维度 ∈ [64, 4096]（变更触发重建引导） */
    @Min(value = 64, message = "向量维度不得小于 64")
    @Max(value = 4096, message = "向量维度不得大于 4096")
    private Integer dimensions;

    /** 索引运算符：vector / hnsw（变更触发重建引导） */
    private String operator;

    private Boolean enabled;
}
