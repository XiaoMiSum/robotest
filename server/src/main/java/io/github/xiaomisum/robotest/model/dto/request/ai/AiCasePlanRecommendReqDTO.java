package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * AI 用例规划智能推荐请求（POST /api/project/ai/cases/plan-recommend，同步，3.5）。
 *
 * <p>text / requirementIds 两种输入至少一项非空（service 层校验）；模块列表输入已随回归子集推荐下线移除。</p>
 */
@Data
public class AiCasePlanRecommendReqDTO {

    /** 需求文本，可空；与 requirementIds 至少一项非空 */
    private String text;

    /** 需求池条目（US-AI-004），可空 */
    @Size(max = 100, message = "需求条目数量不能超过 100")
    private List<UUID> requirementIds;

    /** 当前评审/计划已纳入的用例节点 ID，推荐结果排除这些用例（不重复推荐），可空 */
    @Size(max = 500, message = "排除用例数量不能超过 500")
    private List<UUID> excludeCaseNodeIds;
}
