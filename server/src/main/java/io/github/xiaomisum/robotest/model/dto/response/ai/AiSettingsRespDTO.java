package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.util.UUID;

/**
 * AI 全局配置（GET /api/ai/settings，详设 3.2）。
 */
@Data
public class AiSettingsRespDTO {

    /** AI 总开关 */
    private Boolean enabled;

    /** 默认模型 ID */
    private UUID defaultModelId;

    /** 默认模型名称（未配置为 null） */
    private String defaultModelName;

    /** 任务超时秒数 */
    private Integer taskTimeoutSeconds;

    /** 自动重试次数 */
    private Integer taskMaxRetries;

    /** 可用模型就绪（默认模型配置且启用） */
    private Boolean modelReady;

    /** 向量 API 就绪（已配置且启用） */
    private Boolean embeddingReady;

    /** 入口可见性 = enabled && modelReady */
    private Boolean available;
}
