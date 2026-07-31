package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.service.ai.AiModels.AiCallContext;
import io.github.xiaomisum.robotest.service.ai.AiModels.ChatCallOptions;
import io.github.xiaomisum.robotest.service.ai.AiModels.ChatMessage;
import io.github.xiaomisum.robotest.service.ai.AiModels.ChatResult;
import io.github.xiaomisum.robotest.service.ai.AiModels.EmbedResult;
import io.github.xiaomisum.robotest.service.ai.AiModels.StreamCallbacks;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
@Service
public class AiGatewayServiceImpl implements AiGatewayService {

    private static final long SSE_TIMEOUT_MILLIS = 120_000L;
    private static final long PING_INTERVAL_SECONDS = 15L;

    /** SSE 心跳调度（轻量注释行发送，共享单线程即可） */
    private final ScheduledExecutorService pingScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ai-sse-ping");
        thread.setDaemon(true);
        return thread;
    });

    @Resource
    private AiConfigService aiConfigService;
    @Resource
    private OpenAiCompatProvider provider;
    @Resource
    private PromptAssembler promptAssembler;
    @Resource
    private AiRateLimiter rateLimiter;
    @Resource
    private AiAuditRecorder auditRecorder;
    @Resource
    private AiOutputValidator outputValidator;

    @Override
    public ChatResult complete(AiCallContext context, AiFunctionType functionType,
                               String taskInstruction, String businessData, ChatCallOptions options) {
        ResolvedAiConfig config = requireEnabled();
        checkRateLimit(context, functionType, config);
        List<ChatMessage> messages = promptAssembler.assemble(functionType, taskInstruction, businessData);

        long start = System.currentTimeMillis();
        try {
            ChatResult result = provider.complete(config, messages, options);
            auditRecorder.record(context, functionType, config.chatModel(), System.currentTimeMillis() - start,
                    result.promptTokens(), result.completionTokens(), Constants.AiInvocationStatus.SUCCESS, null);
            return new ChatResult(AiOutputValidator.stripNoise(result.content()),
                    result.promptTokens(), result.completionTokens(), result.finishReason());
        } catch (ServiceException e) {
            auditRecorder.record(context, functionType, config.chatModel(), System.currentTimeMillis() - start,
                    null, null, Constants.AiInvocationStatus.FAILED, "6002");
            throw e;
        }
    }

    @Override
    public <T> T completeStructured(AiCallContext context, AiFunctionType functionType,
                                    String taskInstruction, String businessData, ChatCallOptions options,
                                    Class<T> resultType, Consumer<T> extraAssertion) {
        ResolvedAiConfig config = requireEnabled();
        checkRateLimit(context, functionType, config);
        ChatCallOptions jsonOptions = new ChatCallOptions(options.maxTokens(), options.temperature(),
                true, options.readTimeoutMillis());

        long start = System.currentTimeMillis();
        int promptTokens = 0;
        int completionTokens = 0;
        try {
            List<ChatMessage> messages = promptAssembler.assemble(functionType, taskInstruction, businessData);
            ChatResult first = provider.complete(config, messages, jsonOptions);
            promptTokens += first.promptTokens() != null ? first.promptTokens() : 0;
            completionTokens += first.completionTokens() != null ? first.completionTokens() : 0;
            try {
                T result = outputValidator.parseAndValidate(first.content(), resultType, extraAssertion);
                auditRecorder.record(context, functionType, config.chatModel(), System.currentTimeMillis() - start,
                        promptTokens, completionTokens, Constants.AiInvocationStatus.SUCCESS, null);
                return result;
            } catch (AiOutputValidator.OutputValidationException firstFailure) {
                // 追加校验错误说明重新调用一次（4.4 带错重试）
                String retryInstruction = taskInstruction + "\n\n上一次输出未通过校验，错误说明："
                        + firstFailure.getMessage() + "\n请严格按照输出格式约束重新输出。";
                List<ChatMessage> retryMessages = promptAssembler.assemble(functionType, retryInstruction, businessData);
                ChatResult second = provider.complete(config, retryMessages, jsonOptions);
                promptTokens += second.promptTokens() != null ? second.promptTokens() : 0;
                completionTokens += second.completionTokens() != null ? second.completionTokens() : 0;
                try {
                    T result = outputValidator.parseAndValidate(second.content(), resultType, extraAssertion);
                    auditRecorder.record(context, functionType, config.chatModel(), System.currentTimeMillis() - start,
                            promptTokens, completionTokens, Constants.AiInvocationStatus.SUCCESS, null);
                    return result;
                } catch (AiOutputValidator.OutputValidationException secondFailure) {
                    auditRecorder.record(context, functionType, config.chatModel(), System.currentTimeMillis() - start,
                            promptTokens, completionTokens, Constants.AiInvocationStatus.SCHEMA_INVALID, "6003");
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_OUTPUT_SCHEMA_INVALID);
                }
            }
        } catch (ServiceException e) {
            if (!isSchemaInvalid(e)) {
                auditRecorder.record(context, functionType, config.chatModel(), System.currentTimeMillis() - start,
                        promptTokens, completionTokens, Constants.AiInvocationStatus.FAILED, "6002");
            }
            throw e;
        }
    }

    @Override
    public SseEmitter stream(AiCallContext context, AiFunctionType functionType,
                             String taskInstruction, String businessData, ChatCallOptions options,
                             Consumer<SseEmitter> prelude, Function<String, Object> doneAssembler) {
        // 前置校验在建立 SSE 前同步执行，失败走常规异常响应
        ResolvedAiConfig config = requireEnabled();
        checkRateLimit(context, functionType, config);
        List<ChatMessage> messages = promptAssembler.assemble(functionType, taskInstruction, businessData);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        // 客户端断开/超时即置取消标志，上游读取在下一行边界以取消退出
        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onError(e -> cancelled.set(true));
        emitter.onTimeout(() -> {
            cancelled.set(true);
            emitter.complete();
        });

        ScheduledFuture<?> ping = pingScheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                cancelled.set(true);
            }
        }, PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);

        long start = System.currentTimeMillis();
        Thread.startVirtualThread(() -> {
            try {
                if (prelude != null) {
                    prelude.accept(emitter);
                }
                provider.stream(config, messages, options, new StreamCallbacks() {

                    @Override
                    public void onDelta(String content) {
                        // AI 总开关关闭时在下一帧转发前中断（SRS 3.1）
                        if (!Boolean.TRUE.equals(aiConfigService.getStatus().getEnabled())) {
                            cancelled.set(true);
                            sendError(emitter, ErrorCodeConstants.AI_NOT_ENABLED.code(),
                                    ErrorCodeConstants.AI_NOT_ENABLED.msg());
                            throw new OpenAiCompatProvider.StreamCancelledException();
                        }
                        send(emitter, "delta", Map.of("content", content));
                    }

                    @Override
                    public void onFinish(String fullContent, Integer promptTokens, Integer completionTokens) {
                        Object doneData;
                        try {
                            doneData = doneAssembler != null
                                    ? doneAssembler.apply(fullContent)
                                    : Map.of("content", AiOutputValidator.stripNoise(fullContent));
                        } catch (AiOutputValidator.OutputValidationException e) {
                            auditRecorder.record(context, functionType, config.chatModel(),
                                    System.currentTimeMillis() - start, promptTokens, completionTokens,
                                    Constants.AiInvocationStatus.SCHEMA_INVALID, "6003");
                            sendError(emitter, ErrorCodeConstants.AI_OUTPUT_SCHEMA_INVALID.code(),
                                    ErrorCodeConstants.AI_OUTPUT_SCHEMA_INVALID.msg());
                            emitter.complete();
                            return;
                        }
                        auditRecorder.record(context, functionType, config.chatModel(),
                                System.currentTimeMillis() - start, promptTokens, completionTokens,
                                Constants.AiInvocationStatus.SUCCESS, null);
                        send(emitter, "done", doneData);
                        emitter.complete();
                    }
                }, cancelled);
            } catch (OpenAiCompatProvider.StreamCancelledException e) {
                auditRecorder.record(context, functionType, config.chatModel(), System.currentTimeMillis() - start,
                        null, null, Constants.AiInvocationStatus.CANCELLED, null);
                emitter.complete();
            } catch (Exception e) {
                log.warn("[AI] 流式调用异常: {}", e.getMessage());
                auditRecorder.record(context, functionType, config.chatModel(), System.currentTimeMillis() - start,
                        null, null, Constants.AiInvocationStatus.FAILED, "6002");
                sendError(emitter, ErrorCodeConstants.AI_CALL_FAILED.code(),
                        ErrorCodeConstants.AI_CALL_FAILED.msg());
                emitter.complete();
            } finally {
                ping.cancel(false);
            }
        });
        return emitter;
    }

    @Override
    public List<float[]> embed(AiCallContext context, AiFunctionType functionType, List<String> inputs) {
        ResolvedAiConfig config = requireEnabled();
        if (!config.embeddingConfigured()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_NOT_ENABLED);
        }
        checkRateLimit(context, functionType, config);

        long start = System.currentTimeMillis();
        try {
            EmbedResult result = provider.embed(config, inputs);
            auditRecorder.record(context, functionType, config.embeddingModel(), System.currentTimeMillis() - start,
                    result.promptTokens(), null, Constants.AiInvocationStatus.SUCCESS, null);
            return result.vectors();
        } catch (ServiceException e) {
            auditRecorder.record(context, functionType, config.embeddingModel(), System.currentTimeMillis() - start,
                    null, null, Constants.AiInvocationStatus.FAILED, "6002");
            throw e;
        }
    }

    private ResolvedAiConfig requireEnabled() {
        ResolvedAiConfig config = aiConfigService.getResolvedConfig();
        if (config == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_NOT_ENABLED);
        }
        return config;
    }

    private void checkRateLimit(AiCallContext context, AiFunctionType functionType, ResolvedAiConfig config) {
        try {
            rateLimiter.checkAndRecord(context.userId(), functionType);
        } catch (ServiceException e) {
            // 被限流的请求写审计但不计入窗口
            auditRecorder.record(context, functionType, config.chatModel(), 0, null, null,
                    Constants.AiInvocationStatus.RATE_LIMITED, "6004");
            throw e;
        }
    }

    private boolean isSchemaInvalid(ServiceException e) {
        return ErrorCodeConstants.AI_OUTPUT_SCHEMA_INVALID.msg().equals(e.getMessage());
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            throw new OpenAiCompatProvider.StreamCancelledException();
        }
    }

    private void sendError(SseEmitter emitter, Object code, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Map.of("code", code, "message", message),
                    MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            // 客户端已断开，忽略
        }
    }

    @PreDestroy
    public void shutdown() {
        pingScheduler.shutdownNow();
    }
}
