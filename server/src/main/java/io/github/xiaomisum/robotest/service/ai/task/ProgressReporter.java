package io.github.xiaomisum.robotest.service.ai.task;

/**
 * 进度 / 阶段上报（详设 4.2）：仅对 running 状态的任务生效，已取消的任务不再回写。
 */
@FunctionalInterface
public interface ProgressReporter {

    void report(int percent, String phase);
}
