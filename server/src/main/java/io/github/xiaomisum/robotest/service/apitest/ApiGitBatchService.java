package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiGitBatchManifestRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiGitRepo;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 外部执行批次：清单冻结与流水线触发（GitLab 集成详细设计 4.2）。
 * <p>
 * 硬约束：先落批次记录 + 冻结清单快照，落库成功后才允许触发流水线；触发变量只传
 * ROBOTEST_BATCH_ID 批次票据。单场景入口与定时任务分组共用本服务（WP-8.1 复用触发）。
 */
public interface ApiGitBatchService {

    /**
     * 冻结清单并触发流水线。
     * <p>
     * 顺序为硬约束（详设 4.2 第 2 步）：宁可流水线起来后 CI 取不到清单（CI 侧重试），
     * 也不能流水线已跑而快照未落——后者会让 CI 拿到空清单静默不执行。
     * 触发失败（GitLab 拒绝 1000017704 / 仓库不可达 1000017707）时整批置 error 留痕后抛出。
     *
     * @param repo             已校验可用（存在且启用）的仓库配置
     * @param scenes           本批场景（gitlab_ci 引擎，sourceRef 即类全限定名#方法名）
     * @param scheduledTaskId  关联定时任务，手动执行为 null
     * @param triggerType      manual / scheduled（沿用执行记录既有语义）
     * @param source           scene / schedule（沿用执行记录既有语义）
     * @return 批次票据与首条执行记录 ID（单场景语义即 executionId）
     */
    Result trigger(ApiGitRepo repo, List<ApiScene> scenes, UUID scheduledTaskId,
            String triggerType, String source);

    /**
     * CI 侧取冻结清单（详设 4.6）：按批次票据读取冻结快照，幂等，同一 batchId 多次调用返回同一份。
     *
     * @throws xyz.migoo.framework.common.exception.ServiceException 1000017713，批次不存在
     */
    ApiGitBatchManifestRespDTO getManifest(String batchId);

    /**
     * CI 侧接收结果制品（详设 4.6）：校验后暂存于内存，供完成回报与轮询任一通道回收。
     * <p>
     * 幂等后到覆盖；批次已置终态时接收成功但忽略（与终态去重口径一致，详设 4.6 接口表）。
     *
     * @param zip 结果制品 zip 字节流
     * @throws xyz.migoo.framework.common.exception.ServiceException 1000017713 批次不存在；
     *         1000017714 内容非合法 zip 或超过接收上限 5MB
     */
    void receiveArtifacts(String batchId, byte[] zip);

    /**
     * 批次终态写闸门（详设 4.2 第 5 步终态去重）：仅 pending/running 可转终态。
     * CI 回报与轮询补拉两通道都经此写入，先到者定终态、后到者返回 false 被忽略，
     * 既不丢结果也不会被后到的写入覆盖（BR-1.24）。
     *
     * @param status       终态取值：success / failed / error / cancelled / timeout
     * @param errorMessage 失败原因，终态成功时为 null
     * @return true=本次写入生效；false=批次已置终态，本次写入被忽略
     */
    default boolean finishBatch(String batchId, String status, String errorMessage) {
        return finishBatch(batchId, status, errorMessage, null);
    }

    /**
     * 同上；整批降级时随终态一并写入对账摘要（期望 / 实际用例数与流水线链接，详设 4.6）。
     *
     * @param summary 批次结果摘要，null 表示不触碰该列
     */
    boolean finishBatch(String batchId, String status, String errorMessage, Map<String, Object> summary);

    /** 触发结果：批次票据 + 首条执行记录 ID */
    record Result(String batchId, java.util.UUID executionId) {
    }
}
