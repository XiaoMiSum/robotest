package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiAnalysisTask;

import java.util.UUID;

/**
 * AI 异步任务生命周期管理（创建/执行/取消/重试/联动取消）
 */
public interface AiTaskService {

    /**
     * 创建任务并尝试即时提交执行。
     * 业务任务同 type+target 存在进行中记录时拒绝（6005）；
     * embedding_rebuild 为覆盖式创建（先取消进行中的旧任务）。
     */
    AiAnalysisTask createTask(String type, UUID workspaceId, UUID projectId, UUID targetId, UUID createdBy);

    /**
     * 查询任务（项目级接口：归属项目须与 X-Active-Project 一致）
     */
    AiTaskRespDTO getTask(UUID taskId, UUID projectId);

    /**
     * 取消：仅 pending/running 且仅发起人可操作，其余 6006
     */
    void cancelTask(UUID taskId, UUID userId);

    /**
     * 重试：仅 failed 且仅发起人；同 type+target 已有进行中任务返回 6005
     */
    void retryTask(UUID taskId, UUID userId);

    /**
     * 业务状态变更联动取消（如评审离开「评审中」取消 review_check）
     */
    void cancelByTypeAndTarget(String type, UUID targetId);

    /**
     * AI 总开关关闭联动：全部 pending/running 置 cancelled
     */
    void cancelAllInProgress();

    /**
     * 最近一次 embedding_rebuild 任务（管理端 3.3.5），从未创建返回 null
     */
    AiTaskRespDTO getLatestRebuildTask();

    /**
     * 重试向量重建：仅最近一次为 failed/cancelled 时可重试，任意系统管理员可操作
     */
    void retryRebuildTask();
}
