package io.github.xiaomisum.robotest.service.apitest.execution;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 运行中场景执行的取消标志注册表（接口测试域重构方案 04 §3.1.3）。
 * <p>
 * 单实例内存态假设（总体重构计划 R5）：cancelFlags 复刻原 `SceneExecutionServiceImpl` 平铺字段语义，
 * 仅在同一 JVM 实例内有效，不提供跨实例协调——多实例部署时取消语义由调度层另行保证。
 */
@Component
public final class ExecutionCancelRegistry {

    private final ConcurrentHashMap<UUID, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    /**
     * 为即将执行的 executionId 挂载取消标志（任务起跑时调用）；同一执行重复注册复用既有标志。
     * 与 {@link #release(UUID)} 配对：终态后必须释放，防止无界增长。
     */
    public AtomicBoolean register(UUID executionId) {
        return cancelFlags.compute(executionId,
                (key, existing) -> existing != null ? existing : new AtomicBoolean(false));
    }

    /** 请求取消：命中运行中标志则置位并返回 true；未挂载（队列积压尚未起跑）返回 false */
    public boolean requestCancellation(UUID executionId) {
        AtomicBoolean flag = cancelFlags.get(executionId);
        if (flag == null) {
            return false;
        }
        flag.set(true);
        return true;
    }

    /** 是否已请求取消：用于步骤循环中探测取消信号 */
    public boolean isCancelled(UUID executionId) {
        AtomicBoolean flag = cancelFlags.get(executionId);
        return flag != null && flag.get();
    }

    /** 终态（成功/失败/取消）后清理，防泄漏；重复释放幂等 */
    public void release(UUID executionId) {
        cancelFlags.remove(executionId);
    }
}