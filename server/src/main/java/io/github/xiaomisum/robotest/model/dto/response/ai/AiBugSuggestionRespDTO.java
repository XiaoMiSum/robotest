package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

/**
 * 缺陷表单智能建议响应（3.1）：结果仅回填表单待用户确认，不产生自动提交
 */
@Data
public class AiBugSuggestionRespDTO {

    /** 优化后的缺陷标题 */
    private String optimizedTitle;

    /** fatal / serious / general / minor */
    private String severity;

    /** high / medium / low */
    private String priority;

    /** 建议依据一句话说明 */
    private String reason;
}
