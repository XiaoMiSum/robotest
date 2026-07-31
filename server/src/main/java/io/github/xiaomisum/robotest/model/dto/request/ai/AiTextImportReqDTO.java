package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * AI 外部文本导入解析请求（POST /api/project/ai/minder/import，SSE）。
 *
 * <p>
 * text 长度上限为系统配置项 importTextMaxLength（默认 20000），在 Service 层校验；
 * 自由文本 / Markdown / 制表符分隔内容的格式识别交由 LLM（详细设计 3.2.3）。
 * </p>
 */
@Data
public class AiTextImportReqDTO {

    @NotNull(message = "文档标识不能为空")
    private UUID documentId;

    /** 挂载目标节点：用户未选中节点时由前端传入文档根节点 ID */
    @NotNull(message = "挂载目标节点不能为空")
    private UUID targetNodeId;

    @NotBlank(message = "待解析文本不能为空")
    private String text;

    /** 用户选择的对话模型，可空（缺省或失效回退系统默认，4.11） */
    private UUID modelId;
}
