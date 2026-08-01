package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.util.UUID;

/**
 * 发起评审检查响应（3.1.1）：返回异步任务 ID，前端轮询任务状态
 */
@Data
public class AiReviewCheckStartRespDTO {

    private UUID taskId;
}
