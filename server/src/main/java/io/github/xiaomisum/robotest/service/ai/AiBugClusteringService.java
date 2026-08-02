package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.model.dto.response.ai.AiBugClusteringStartRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskRespDTO;

import java.util.UUID;

/**
 * 缺陷聚类分析（US-AI-010，详细设计 3.3）：异步任务，结果快照结构见设计 2.3
 */
public interface AiBugClusteringService {

    /**
     * 发起聚类任务：项目内无未关闭缺陷返回 6012；同项目进行中任务由任务框架 6005 拦截
     */
    AiBugClusteringStartRespDTO startClustering(UUID userId, UUID workspaceId, UUID projectId);

    /**
     * 最近一次聚类任务（无记录返回 null，3.3.2）
     */
    AiTaskRespDTO getLatestClustering(UUID projectId);
}
