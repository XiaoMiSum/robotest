package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 场景提示词详情（GET /api/ai/prompts/{scene}，详设 3.5）。
 */
@Data
public class AiPromptDetailRespDTO {

    private String scene;

    private String name;

    /** 自定义行内容；无自定义行时为内置默认 */
    private String content;

    private List<AiPromptVariableDTO> variables;

    /** default / custom */
    private String source;

    /** 自定义版本号；无自定义行为 0 */
    private Integer version;

    private LocalDateTime updatedAt;
}
