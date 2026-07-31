package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * AI 补全步骤请求（POST /api/project/ai/cases/complete-steps，SSE）。
 *
 * <p>
 * 梯队一不含需求池条目选取（requirementIds），随 US-AI-004 交付后向后兼容扩展（详细设计 3.2.2 / 第 7 章）。
 * </p>
 */
@Data
public class AiStepCompleteReqDTO {

    @NotNull(message = "文档标识不能为空")
    private UUID documentId;

    /** 待补全的用例节点（必须为 case 类型） */
    @NotNull(message = "用例节点不能为空")
    private UUID nodeId;

    /** 临时补充上下文，可空 */
    private String extraText;

    /** 用户选择的对话模型，可空（缺省或失效回退系统默认，4.11） */
    private UUID modelId;
}
