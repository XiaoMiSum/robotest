package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * AI 补全步骤请求（POST /api/project/ai/cases/complete-steps，SSE）。
 *
 * <p>上下文取材含需求池条目（requirementIds，US-AI-004）与临时补充文本（extraText），均可空。</p>
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

    /** 需求池条目（US-AI-004），可空 */
    @Size(max = 100, message = "需求条目数量不能超过 100")
    private List<UUID> requirementIds;

    /** 用户选择的对话模型，可空（缺省或失效回退系统默认，4.11） */
    private UUID modelId;
}
