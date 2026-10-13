package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiGitBatchCompletionReqDTO;

/**
 * CI 回报批次完成（GitLab 集成详细设计 4.6 completion 端点）。
 * <p>
 * CI 在 after_script 回调本入口：平台将 CI 自报计数合入批次记录 summary 后触发
 * completion 通道结果回收；制品在 after_script 之后才由 GitLab 上传，此刻常取不到，
 * 回收端推迟给流水线轮询通道，本入口不阻塞 CI。批次已置终态时本次写入被忽略
 * （终态去重，BR-1.24），回收本身亦有单飞与逐条条件终态兜底，重复回报幂等。
 */
public interface ApiGitCompletionService {

    /**
     * 回报批次终态与用例计数。
     * <p>
     * 批次不存在返回 1000017713；status 仅接受 success / failed（CI 侧无取消语义，
     * 流水线取消由轮询通道按流水线状态映射处理）。
     *
     * @param batchId    批次票据（触发流水线时注入 ROBOTEST_BATCH_ID）
     * @param requestDTO CI 自报状态与用例计数
     */
    void reportCompletion(String batchId, ApiGitBatchCompletionReqDTO requestDTO);
}
