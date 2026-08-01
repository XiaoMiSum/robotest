package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * AI 生成用例子树请求（POST /api/project/ai/cases/generate，SSE）。
 *
 * <p>需求输入二选一：手动文本（requirementText）或需求池条目（requirementIds）；
 * 同时支持生成前将临时文本另存为需求池条目（saveAsRequirement，3.2.1）。</p>
 */
@Data
public class AiCaseGenerateReqDTO {

    @NotNull(message = "文档标识不能为空")
    private UUID documentId;

    /** 挂载目标节点：用户未选中节点时由前端传入文档根节点 ID（3.2.1） */
    @NotNull(message = "挂载目标节点不能为空")
    private UUID targetNodeId;

    /** 手动需求文本，可空；与 requirementIds 至少一项非空（3.2.1，service 层校验） */
    private String requirementText;

    /** 需求池条目（US-AI-004），可空；与 requirementText 至少一项非空 */
    @Size(max = 100, message = "需求条目数量不能超过 100")
    private List<UUID> requirementIds;

    /** 生成开始前将 requirementText 另存为需求池条目（非空时 requirementText 必须非空） */
    private SaveAsRequirement saveAsRequirement;

    /** 用户选择的对话模型，可空（缺省或失效回退系统默认，4.11） */
    private UUID modelId;

    @Data
    public static class SaveAsRequirement {

        @NotBlank(message = "条目标题不能为空")
        @Size(max = 200, message = "条目标题不能超过 200 字符")
        private String title;
    }
}
