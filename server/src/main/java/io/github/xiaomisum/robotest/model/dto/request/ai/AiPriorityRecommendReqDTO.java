package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * AI 优先级推荐请求（POST /api/project/ai/cases/priority-recommend，同步，详细设计 3.3.1）。
 *
 * <p>
 * 由前端在手工单节点标记为用例类型时触发；ancestorTitles 为节点祖先链标题（不含自身），
 * 供 LLM 结合模块路径判定，可空。
 * </p>
 */
@Data
public class AiPriorityRecommendReqDTO {

    /** 用例标题（规则引擎与 LLM 判定的核心输入） */
    @NotBlank(message = "用例标题不能为空")
    @Size(max = 200, message = "用例标题长度不能超过 200")
    private String title;

    /** 祖先链标题（从文档根至父节点），可空 */
    @Size(max = 50, message = "祖先路径层级不能超过 50")
    private List<@Size(max = 200, message = "模块标题长度不能超过 200") String> ancestorTitles;
}
