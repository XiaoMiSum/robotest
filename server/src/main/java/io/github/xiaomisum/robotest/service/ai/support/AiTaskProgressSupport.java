package io.github.xiaomisum.robotest.service.ai.support;

import io.github.xiaomisum.robotest.repository.ai.AiAnalysisTaskMapper;

import java.util.UUID;

/**
 * 批处理任务进度基础设施（基础设施 4.6，聚类/重建/检查共用）：
 * 进度百分比换算 + 心跳写入，取消语义（影响行数 0 = 中止）集中在同一处。
 */
public final class AiTaskProgressSupport {

    private AiTaskProgressSupport() {
    }

    /** 进度百分比：总数为 0 视为 0%，避免除零；结果四舍五入到整数百分比 */
    public static int percent(int done, int total) {
        return total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
    }

    /**
     * 进度心跳：写入进度与累计结果 JSON（部分快照）；返回影响行数，
     * 0 表示任务已被取消/置失败，调用方应在批次边界立即中止并返回部分结果（基础设施 4.6）。
     */
    public static int heartbeat(AiAnalysisTaskMapper mapper, UUID taskId, int progress, String resultJson) {
        return mapper.updateProgressIfRunning(taskId, progress, resultJson);
    }
}
