package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.model.entity.ai.AiAnalysisTask;

import java.util.Map;

/**
 * 异步任务执行处理器 —— 各任务类型（review_check / bug_clustering / embedding_rebuild）
 * 由对应业务模块实现并注册为 Spring Bean，AiTaskService 按 type 分发。
 */
public interface AiTaskHandler {

    /**
     * 处理的任务类型（Constants.AiTaskType）
     */
    String type();

    /**
     * 执行任务：分批调用须在批次边界更新 progress（触发 updated_at 心跳）并检查任务是否已被取消。
     *
     * @return 成功结果快照（写入 ai_analysis_task.result）
     */
    Map<String, Object> execute(AiAnalysisTask task);
}
