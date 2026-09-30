package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * AI 遗漏测试点分析请求（POST /api/project/ai/cases/missing-points，同步，3.3）。
 *
 * <p>documentIds 不可空（分析范围）；text 与 requirementIds 至少一项非空（service 层校验）。</p>
 */
@Data
public class AiMissingPointReqDTO {

    /** 分析范围：当前脑图文档 ID 列表，不可空 */
    @NotEmpty(message = "文档列表不能为空")
    @Size(max = 20, message = "文档数量不能超过 20")
    private List<UUID> documentIds;

    /** 直接粘贴的需求文本，可空；与 requirementIds 至少一项非空 */
    private String text;

    /** 需求池条目（US-AI-004），可空；与 text 至少一项非空 */
    @Size(max = 100, message = "需求条目数量不能超过 100")
    private List<UUID> requirementIds;

    /** 对话模型，可空；空则走系统默认（交互设计 56 §1.2 选择模型） */
    private UUID modelId;
}
