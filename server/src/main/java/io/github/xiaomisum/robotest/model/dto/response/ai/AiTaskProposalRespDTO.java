package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 提案详情（详设 3.6.3 提案详情端点）：结构化产物 + 证据链，内容结构由各分册定义。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiTaskProposalRespDTO extends AiTaskProposalSummaryRespDTO {

    /** 结构化产物 + 证据链 */
    private Map<String, Object> content;
}
