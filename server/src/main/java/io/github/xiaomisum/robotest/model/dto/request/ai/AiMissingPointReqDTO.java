package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * AI 遗漏测试点分析请求（POST /api/project/ai/cases/missing-points，同步，3.3）。
 *
 * <p>keywords / text / requirementIds 三种输入至少一项非空（service 层校验）。</p>
 */
@Data
public class AiMissingPointReqDTO {

    /** 直接输入的关键词，可空；与 text/requirementIds 至少一项非空 */
    @Size(max = 20, message = "关键词数量不能超过 20")
    private List<String> keywords;

    /** 直接粘贴的需求文本，可空 */
    private String text;

    /** 需求池条目（US-AI-004），可空；与 keywords/text 至少一项非空 */
    @Size(max = 100, message = "需求条目数量不能超过 100")
    private List<UUID> requirementIds;
}
