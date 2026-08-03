package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * AI 回归测试用例子集推荐请求（POST /api/project/ai/plans/regression-recommend，同步，3.5）。
 *
 * <p>modules / text / requirementIds 三种输入至少一项非空（service 层校验）；
 * saveAsRequirement 非空时先将 text 另存为需求池条目（失败不阻断推荐，约定同 3.3）。</p>
 */
@Data
public class AiRegressionRecommendReqDTO {

    /** 变更模块名（模块树名称精确 + ILIKE 模糊匹配），可空；与 text/requirementIds 至少一项非空 */
    @Size(max = 50, message = "模块数量不能超过 50")
    private List<String> modules;

    /** 变更说明文本，可空 */
    private String text;

    /** 需求池条目（US-AI-004），可空 */
    @Size(max = 100, message = "需求条目数量不能超过 100")
    private List<UUID> requirementIds;

    /** 推荐开始前将 text 另存为需求池条目（非空时 text 必须非空，约定同 3.3） */
    private SaveAsRequirement saveAsRequirement;

    @Data
    public static class SaveAsRequirement {

        @NotBlank(message = "条目标题不能为空")
        @Size(max = 200, message = "条目标题不能超过 200 字符")
        private String title;
    }
}
