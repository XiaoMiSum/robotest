package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.UUID;

/**
 * 更新 AI 全局配置（PUT /api/ai/settings，详设 3.2；部分更新）。
 */
@Data
public class AiSettingsUpdateReqDTO {

    /** AI 总开关 */
    private Boolean enabled;

    /** 默认模型（引用模型配置；被引用的模型禁止删除） */
    private UUID defaultModelId;

    /** 任务超时秒数 ∈ [30, 3600]（超出由参数校验统一拒绝） */
    @Min(value = 30, message = "任务超时秒数不得小于 30")
    @Max(value = 3600, message = "任务超时秒数不得大于 3600")
    private Integer taskTimeoutSeconds;

    /** 自动重试次数 ∈ [0, 5]（超出由参数校验统一拒绝） */
    @Min(value = 0, message = "重试次数不得小于 0")
    @Max(value = 5, message = "重试次数不得大于 5")
    private Integer taskMaxRetries;
}
