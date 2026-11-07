package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

/**
 * 提案清单摘要（详设 3.6.3 proposals）：不含 content，完整内容经提案详情端点获取。
 */
@Data
public class AiTaskProposalSummaryRespDTO {

    /** 提案在本次执行产出中的定位键 */
    private String key;

    /** 提案类型（取值见各分册） */
    private String kind;

    private String title;

    /** 树形提案的父提案键，非树形为 null */
    private String parentKey;

    /**
     * 确认状态：pending（待采纳）/ not_applicable（只读建议）/
     * adopted / adopted_edited / rejected（已有采纳记录时回填动作，总册 3.6.3）。
     */
    private String confirmStatus;
}
