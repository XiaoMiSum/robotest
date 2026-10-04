package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

/**
 * 模型连通性测试结果（POST /api/ai/models/{modelId}/test，详设 3.3）。
 */
@Data
public class AiModelTestRespDTO {

    private Boolean success;

    private Integer latencyMs;

    private String msg;
}
