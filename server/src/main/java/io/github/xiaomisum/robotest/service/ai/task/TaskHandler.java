package io.github.xiaomisum.robotest.service.ai.task;

import io.github.xiaomisum.robotest.framework.security.LoginUser;

import java.util.Map;

/**
 * 任务处理器 SPI（详设 4.2）：每个 type 注册一个 handler，负责输入校验、执行与产物结构（result.artifacts）。
 * 批次一由需求拆分 / 导入分册注册实现；未注册的枚举类型提交时报 1000018114。
 */
public interface TaskHandler {

    /** 任务类型（详设 3.6.1 枚举值） */
    String type();

    /** 内置默认提示词：无自定义场景行时的回落内容（详设 3.5 source = default 口径） */
    default String defaultPrompt() {
        return null;
    }

    /** 附加资源权限（3.6.2「ai:task + 对应资源权限」）；仅 HTTP 提交携带登录态时调用 */
    default void checkPermission(LoginUser loginUser) {
    }

    /** 输入校验，失败抛 ServiceException(1000018115)（3.6.2） */
    void validateInput(Map<String, Object> input);

    /**
     * 带提交上下文的输入校验（3.6.2 校验顺序「类型 → 输入」）：需要项目范围（归属、去重、状态）
     * 或回写快照的处理器重写本方法，默认回落无上下文实现。
     */
    default void validateInput(Map<String, Object> input, TaskSubmitContext context) {
        validateInput(input);
    }

    TaskResult execute(TaskExecutionContext context);
}
