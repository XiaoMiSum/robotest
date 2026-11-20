package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 向量 API 连通性测试（POST /api/ai/embedding/test，详设 3.4）。
 * 字段均可缺省：缺省项由已保存配置补齐，仅本次调用使用，不写回配置表。
 */
@Data
public class AiEmbeddingTestReqDTO {

    private String provider;

    @Size(max = 500, message = "端点地址长度不能超过 500")
    private String baseUrl;

    /** 密钥明文；为空则沿用已保存密钥，非空仅加密用于本次调用 */
    @Size(max = 500, message = "密钥长度不能超过 500")
    private String apiKey;

    @Size(max = 100, message = "嵌入模型长度不能超过 100")
    private String embeddingModel;

    @Min(value = 64, message = "向量维度不得小于 64")
    @Max(value = 4096, message = "向量维度不得大于 4096")
    private Integer dimensions;

    /** 距离算子：cosine / l2 / inner_product */
    private String operator;
}
