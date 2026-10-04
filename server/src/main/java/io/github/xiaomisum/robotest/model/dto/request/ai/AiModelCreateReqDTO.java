package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建模型配置（POST /api/ai/models，详设 3.3）。
 */
@Data
public class AiModelCreateReqDTO {

    /** 配置名称，唯一（冲突为 1000018103） */
    @NotBlank(message = "配置名称不能为空")
    @Size(max = 50, message = "配置名称长度不能超过 50")
    private String name;

    /** 供应商类别：openai / anthropic / azure / gemini / ollama / custom */
    @NotBlank(message = "供应商类别不能为空")
    private String provider;

    /** 模型端点 */
    @NotBlank(message = "端点地址不能为空")
    @Size(max = 500, message = "端点地址长度不能超过 500")
    private String baseUrl;

    /** 密钥明文（AES 加密落库，接口不回显；创建必填） */
    @NotBlank(message = "密钥不能为空")
    @Size(max = 500, message = "密钥长度不能超过 500")
    private String apiKey;

    /** 实际调用的模型标识 */
    @NotBlank(message = "模型标识不能为空")
    @Size(max = 100, message = "模型标识长度不能超过 100")
    private String modelName;

    /** 能力标签 ⊆ {chat, vision, embedding} */
    @NotEmpty(message = "能力标签不能为空")
    private List<String> capabilities;

    /** 兜底顺序，默认 100 */
    @Min(value = 0, message = "优先级不得小于 0")
    private Integer priority;

    /** 启停开关 */
    private Boolean enabled;

    /** 输入侧每百万 token 单价，≥ 0（0 = 不计价） */
    @DecimalMin(value = "0", message = "输入单价不得小于 0")
    private BigDecimal inputPrice;

    /** 输出侧每百万 token 单价，≥ 0 */
    @DecimalMin(value = "0", message = "输出单价不得小于 0")
    private BigDecimal outputPrice;
}
