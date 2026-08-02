package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.util.UUID;

/**
 * 发起缺陷聚类响应（3.3.1）：返回异步任务 ID，前端轮询任务状态
 */
@Data
public class AiBugClusteringStartRespDTO {

    private UUID taskId;
}
