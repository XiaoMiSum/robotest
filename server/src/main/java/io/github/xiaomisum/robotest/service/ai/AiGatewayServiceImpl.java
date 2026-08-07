package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.AiCallContext;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ChatCallOptions;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ChatMessage;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ChatResult;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.EmbedResult;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.StreamCallbacks;
import io.github.xiaomisum.robotest.service.ai.provider.OpenAiCompatProvider;
import io.github.xiaomisum.robotest.service.ai.provider.PromptAssembler;
import io.github.xiaomisum.robotest.service.ai.provider.ResolvedAiConfig;
import io.github.xiaomisum.robotest.service.ai.provider.ResolvedChatModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
@Service
public class AiGatewayServiceImpl implements AiGatewayService {

    @Resource
    private AiSseSupport sseSupport;
    @Resource
    private AiConfigService aiConfigService;
    @Resource
    private AiChatModelService aiChatModelService;
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
        ResolvedChatModel config = requireChatModel(context);
        checkRateLimit(context, functionType, config.model());
        List<ChatMessage> messages = promptAssembler.assemble(functionType, taskInstruction, businessData);

        long start = System.currentTimeMillis();
        try {
            ChatResult result = provider.complete(config, messages, options);
            auditRecorder.record(context, functionType, config.model(), System.currentTimeMillis() - start,
                    result.promptTokens(), result.completionTokens(), Constants.AiInvocationStatus.SUCCESS, null);
            return new ChatResult(AiOutputValidator.stripNoise(result.content()),
                    result.promptTokens(), result.completionTokens(), result.finishReason());
        } catch (ServiceException e) {
            auditRecorder.record(context, functionType, config.model(), System.currentTimeMillis() - start,
                    null, null, Constants.AiInvocationStatus.FAILED, "6002");
            throw e;
        }
    }

    @Override
    public <T> T completeStructured(AiCallContext context, AiFunctionType functionType,
                                    String taskInstruction, String businessData, ChatCallOptions options,
                                    Class<T> resultType, Consumer<T> extraAssertion) {
        ResolvedChatModel config = requireChatModel(context);
        checkRateLimit(context, functionType, config.model());
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
                auditRecorder.record(context, functionType, config.model(), System.currentTimeMillis() - start,
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
                    auditRecorder.record(context, functionType, config.model(), System.currentTimeMillis() - start,
                            promptTokens, completionTokens, Constants.AiInvocationStatus.SUCCESS, null);
                    return result;
                } catch (AiOutputValidator.OutputValidationException secondFailure) {
                    auditRecorder.record(context, functionType, config.model(), System.currentTimeMillis() - start,
                            promptTokens, completionTokens, Constants.AiInvocationStatus.SCHEMA_INVALID, "6003");
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_OUTPUT_SCHEMA_INVALID);
                }
            }
        } catch (ServiceException e) {
            if (!isSchemaInvalid(e)) {
                auditRecorder.record(context, functionType, config.model(), System.currentTimeMillis() - start,
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
        ResolvedChatModel config = requireChatModel(context);
        checkRateLimit(context, functionType, config.model());
        List<ChatMessage> messages = promptAssembler.assemble(functionType, taskInstruction, businessData);

        AiSseSupport.Channel channel = sseSupport.open();
        SseEmitter emitter = channel.emitter();
        AtomicBoolean cancelled = channel.cancelled();

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
                            sseSupport.sendError(emitter, ErrorCodeConstants.AI_NOT_ENABLED.code(),
                                    ErrorCodeConstants.AI_NOT_ENABLED.msg());
                            throw new OpenAiCompatProvider.StreamCancelledException();
                        }
                        sseSupport.send(emitter, "delta", Map.of("content", content));
                    }

                    @Override
                    public void onFinish(String fullContent, Integer promptTokens, Integer completionTokens) {
                        Object doneData;
                        try {
                            doneData = doneAssembler != null
                                    ? doneAssembler.apply(fullContent)
                                    : Map.of("content", AiOutputValidator.stripNoise(fullContent));
                        } catch (AiOutputValidator.OutputValidationException e) {
                            auditRecorder.record(context, functionType, config.model(),
                                    System.currentTimeMillis() - start, promptTokens, completionTokens,
                                    Constants.AiInvocationStatus.SCHEMA_INVALID, "6003");
                            sseSupport.sendError(emitter, ErrorCodeConstants.AI_OUTPUT_SCHEMA_INVALID.code(),
                                    ErrorCodeConstants.AI_OUTPUT_SCHEMA_INVALID.msg());
                            emitter.complete();
                            return;
                        }
                        auditRecorder.record(context, functionType, config.model(),
                                System.currentTimeMillis() - start, promptTokens, completionTokens,
                                Constants.AiInvocationStatus.SUCCESS, null);
                        sseSupport.send(emitter, "done", doneData);
                        emitter.complete();
                    }
                }, cancelled);
            } catch (OpenAiCompatProvider.StreamCancelledException e) {
                auditRecorder.record(context, functionType, config.model(), System.currentTimeMillis() - start,
                        null, null, Constants.AiInvocationStatus.CANCELLED, null);
                emitter.complete();
            } catch (Exception e) {
                log.warn("[AI] 流式调用异常: {}", e.getMessage());
                auditRecorder.record(context, functionType, config.model(), System.currentTimeMillis() - start,
                        null, null, Constants.AiInvocationStatus.FAILED, "6002");
                sseSupport.sendError(emitter, ErrorCodeConstants.AI_CALL_FAILED.code(),
                        ErrorCodeConstants.AI_CALL_FAILED.msg());
                emitter.complete();
            } finally {
                channel.stopPing();
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
        checkRateLimit(context, functionType, config.embeddingModel());

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

    /**
     * 解析对话调用的运行期模型：先校验总开关（配置存在/开关开/密钥有效），再按 context.modelId
     * 解析已启用模型（缺省/失效回退系统默认，4.11）；任一门槛不满足按 AI 未启用处理。
     */
    private ResolvedChatModel requireChatModel(AiCallContext context) {
        if (aiConfigService.getResolvedConfig() == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_NOT_ENABLED);
        }
        ResolvedChatModel model = aiChatModelService.resolve(context.modelId());
        if (model == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_NOT_ENABLED);
        }
        return model;
    }

    private void checkRateLimit(AiCallContext context, AiFunctionType functionType, String modelName) {
        try {
            rateLimiter.checkAndRecord(context.userId(), functionType);
        } catch (ServiceException e) {
            // 被限流的请求写审计但不计入窗口
            auditRecorder.record(context, functionType, modelName, 0, null, null,
                    Constants.AiInvocationStatus.RATE_LIMITED, "6004");
            throw e;
        }
    }

    private boolean isSchemaInvalid(ServiceException e) {
        return ErrorCodeConstants.AI_OUTPUT_SCHEMA_INVALID.msg().equals(e.getMessage());
    }
}
