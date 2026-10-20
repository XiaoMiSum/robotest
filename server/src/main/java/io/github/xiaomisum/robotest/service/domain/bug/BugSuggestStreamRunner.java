package io.github.xiaomisum.robotest.service.domain.bug;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 建议流异步起跑（详设 3.5）：独立组件承载 @Async，规避 Service 自调用失效
 * （同 AiTaskExecutor 先例）；复用默认 applicationTaskExecutor，装配与模型调用
 * 不占请求线程，控制器先行返回 SseEmitter 使事件实时到达。
 */
@Component
public class BugSuggestStreamRunner {

    @Async
    public void run(Runnable task) {
        task.run();
    }
}
