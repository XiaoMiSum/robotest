package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

/**
 * AI 需求文档拆分请求（POST /api/project/ai/requirements/split，SSE，US-AI-019）。
 *
 * <p>
 * text 长度上限复用系统配置项 importTextMaxLength（默认 20000），在 Service 层校验：
 * 超限截断 + warning 提示，不拒绝（详细设计 3.2.3）。
 * </p>
 */
@Data
public class AiRequirementSplitReqDTO {

    @NotBlank(message = "待拆分文档不能为空")
    private String text;

    /** 用户选择的对话模型，可空（缺省或失效回退系统默认，4.11） */
    private UUID modelId;
}
