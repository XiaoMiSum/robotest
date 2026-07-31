package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * AI 生成用例子树请求（POST /api/project/ai/cases/generate，SSE）。
 *
 * <p>
 * 梯队一仅支持手动输入需求文本；需求池条目选取（requirementIds/saveAsRequirement）
 * 随 US-AI-004 交付后向后兼容扩展（详细设计 3.2.1 / 第 7 章）。
 * </p>
 */
@Data
public class AiCaseGenerateReqDTO {

    @NotNull(message = "文档标识不能为空")
    private UUID documentId;

    /** 挂载目标节点：用户未选中节点时由前端传入文档根节点 ID（3.2.1） */
    @NotNull(message = "挂载目标节点不能为空")
    private UUID targetNodeId;

    @NotBlank(message = "需求文本不能为空")
    private String requirementText;

    /** 用户选择的对话模型，可空（缺省或失效回退系统默认，4.11） */
    private UUID modelId;
}
