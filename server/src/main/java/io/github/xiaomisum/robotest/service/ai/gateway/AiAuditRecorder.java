package io.github.xiaomisum.robotest.service.ai.gateway;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.model.entity.ai.AiInvocationLog;
import io.github.xiaomisum.robotest.repository.ai.AiInvocationLogMapper;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.AiCallContext;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 调用审计 —— 独立单线程执行器异步落库，失败仅记 WARN 不影响调用主链路。
 *
 * <p>只记录调用元数据，不存储 Prompt 与生成内容（SRS 4.2）。</p>
 */
@Slf4j
@Component
public class AiAuditRecorder {

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ai-audit");
        thread.setDaemon(true);
        return thread;
    });

    @Resource
    private AiInvocationLogMapper aiInvocationLogMapper;

    public void record(AiCallContext context, AiFunctionType functionType, String model,
                       long durationMs, Integer promptTokens, Integer completionTokens,
                       String status, String errorCode) {
        AiInvocationLog entry = new AiInvocationLog();
        entry.setUserId(context.userId());
        entry.setWorkspaceId(context.workspaceId());
        entry.setProjectId(context.projectId());
        entry.setFunctionType(functionType.getCode());
        entry.setModel(model);
        entry.setDurationMs((int) Math.min(durationMs, Integer.MAX_VALUE));
        entry.setPromptTokens(promptTokens);
        entry.setCompletionTokens(completionTokens);
        entry.setStatus(status);
        entry.setErrorCode(errorCode);
        executor.execute(() -> {
            try {
                aiInvocationLogMapper.insert(entry);
            } catch (Exception e) {
                log.warn("[AI] 审计日志落库失败: {}", e.getMessage());
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
