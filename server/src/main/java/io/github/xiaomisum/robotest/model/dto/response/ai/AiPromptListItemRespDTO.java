package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 场景提示词列表行（GET /api/ai/prompts，详设 3.5；列：场景 / 摘要 / 更新人 / 更新时间）。
 */
@Data
public class AiPromptListItemRespDTO {

    private String scene;

    private String name;

    /** 内容摘要（首行截断展示） */
    private String summary;

    /** default / custom */
    private String source;

    /** 更新人展示名（无自定义行为 null） */
    private String updatedByName;

    /** 最近更新时间（无自定义行为内置默认的登记无时间，为 null） */
    private LocalDateTime updatedAt;
}
