package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 更新模型配置（PUT /api/ai/models/{modelId}，详设 3.3；部分更新，
 * apiKey 传入即替换、不传保持原值）。
 */
@Data
public class AiModelUpdateReqDTO {

    @Size(max = 50, message = "配置名称长度不能超过 50")
    private String name;

    private String provider;

    @Size(max = 500, message = "端点地址长度不能超过 500")
    private String baseUrl;

    /** 密钥明文；null 表示不替换 */
    @Size(max = 500, message = "密钥长度不能超过 500")
    private String apiKey;

    @Size(max = 100, message = "模型标识长度不能超过 100")
    private String modelName;

    /** 能力标签 ⊆ {chat, vision, embedding} */
    private List<String> capabilities;

    @Min(value = 0, message = "优先级不得小于 0")
    private Integer priority;

    private Boolean enabled;

    @DecimalMin(value = "0", message = "输入单价不得小于 0")
    private BigDecimal inputPrice;

    @DecimalMin(value = "0", message = "输出单价不得小于 0")
    private BigDecimal outputPrice;
}
