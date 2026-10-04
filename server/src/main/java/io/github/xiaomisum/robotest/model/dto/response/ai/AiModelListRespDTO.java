package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.util.List;

/**
 * 模型配置列表（GET /api/ai/models，详设 3.3：{ list, total }）。
 */
@Data
public class AiModelListRespDTO {

    private List<AiModelRespDTO> list;

    private Long total;
}
