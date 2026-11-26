package io.github.xiaomisum.robotest.service.ai.config;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiUsageLog;
import io.github.xiaomisum.robotest.repository.ai.AiUsageLogMapper;
import io.github.xiaomisum.robotest.service.ai.assistant.tool.AssistantToolDefinition;
import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.util.JsonUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OpenAI 兼容模型客户端（详设 4.2 / 4.3）：传输、序列化、重试与响应解析统一由 Spring AI 2.0.x 执行，
 * 平台仅保留门面——按行配置解析、密钥解密、逐次用量记账与 1000018117 错误码映射。
 * 重试承接：客户端 maxRetries 取 task_max_retries（尝试总数 1 + N，与原自实现循环一致），
 * 429 / 5xx 指数退避、4xx 不重试；每次逻辑调用（成功与失败）记一条 ai_usage_log，延迟为端到端耗时。
 * 模型服务不可用 / 超时 / 限流耗尽后抛 1000018117。
 */
@Component
public class AiModelClient {

    private static final Logger log = LoggerFactory.getLogger(AiModelClient.class);

    /** 单次生成请求超时：仅连通性测试等阻塞式 call 路径使用；流式路径改由首 token / 空闲超时兜底 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);
    /** 流式护栏（详设 4.2）：首 token 等待上限与相邻 chunk 空闲上限，内置常量不开放配置 */
    private static final Duration FIRST_TOKEN_TIMEOUT = Duration.ofSeconds(600);
    private static final Duration IDLE_TIMEOUT = Duration.ofSeconds(30);
    /** 长流期间心跳刷新间隔：与 IDLE_TIMEOUT 对齐，防止心跳清扫误杀长流 */
    private static final Duration STREAM_HEARTBEAT_INTERVAL = Duration.ofSeconds(30);
    /** 流式底层读取超时上限：远大于首 token 护栏，避免框架默认 60s 掐断长流；实际终止由 Reactor 护栏负责 */
    private static final Duration STREAM_UNDERLYING_TIMEOUT = Duration.ofHours(24);
    private static final int ERROR_MSG_MAX_LENGTH = 500;
    /** 密钥缺失 / 解密失败是配置类原因，不能用模型调用的通用文案替代（详设总册 4.5） */
    private static final String KEY_FAILURE_REASON = "模型密钥未配置或解密失败";

    /** 护栏间隔为实例字段，默认取内置常量；测试可注入短值以验证各分支 */
    private Duration firstTokenTimeout = FIRST_TOKEN_TIMEOUT;
    private Duration idleTimeout = IDLE_TIMEOUT;
    private Duration streamHeartbeatInterval = STREAM_HEARTBEAT_INTERVAL;

    /** 末跳纯文本归因场景（详设 4.3：判别与查询跳归 assistant_parse、回答生成归 assistant_answer） */
    private static final String SCENE_ANSWER = "assistant_answer";

    /** 平台业务加密密钥（SecretCryptoUtil 多域共用，4.1 沿用既有机制），未配置时解密失败降级 1000018117 */
    private final byte[] secretKey;

    @Resource
    private AiUsageLogMapper usageLogMapper;
    @Resource
    private AiSettingsReader settingsReader;

    public AiModelClient(@Value("${robotest.env.secret-key:}") String base64SecretKey) {
        this.secretKey = SecretCryptoUtil.parseKey(base64SecretKey);
    }

    public AiChatReply chat(AiChatRequest request) {
        String apiKey = secretKey == null ? null
                : SecretCryptoUtil.decrypt(secretKey, request.model().getApiKeyEncrypted());
        if (apiKey == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), KEY_FAILURE_REASON);
        }
        long start = System.currentTimeMillis();
        try {
            ChatModel model = buildChatModel(request, apiKey);
            AiChatReply reply = extractReply(model.call(buildPrompt(request)));
            writeUsage(request, System.currentTimeMillis() - start, true, reply.tokensIn(), reply.tokensOut(),
                    null);
            return reply;
        } catch (Exception e) {
            logFailure(request.promptScene(), e);
            int errorCode = ErrorCodeConstants.AI_MODEL_CALL_FAILED.code();
            writeUsage(request, System.currentTimeMillis() - start, false, 0, 0, errorCode);
            throw toServiceException(e, errorCode);
        }
    }

    /**
     * 带工具声明的单跳调用（详设 4.1 工具调用循环）：工具仅声明不执行——复核 OpenAiChatModel
     * 字节码只经 resolveToolDefinitions（序列化进请求），不调用 executeToolCalls，
     * toolCalls 随响应原样返回，执行权全程在服务端循环。
     * 记账 scene 按结果落账：有 toolCalls 归因请求场景（assistant_parse），纯文本归因 assistant_answer。
     */
    public AiChatReply chatWithTools(AiChatRequest request, List<AssistantToolDefinition> toolDefinitions) {
        return chatWithTools(request, toolDefinitions, List.of());
    }

    /**
     * 带工具声明的单跳调用（详设 4.1 工具调用循环）：工具仅声明不执行——复核 OpenAiChatModel
     * 字节码只经 resolveToolDefinitions（序列化进请求），不调用 executeToolCalls，
     * toolCalls 随响应原样返回，执行权全程在服务端循环。
     * 记账 scene 按结果落账：有 toolCalls 归因请求场景（assistant_parse），纯文本归因 assistant_answer。
     * priorTurns 为循环内既往轮次（assistant tool_calls 与 tool 回填，OpenAI 协议要求成对出现）。
     */
    public AiChatReply chatWithTools(AiChatRequest request, List<AssistantToolDefinition> toolDefinitions,
            List<Message> priorTurns) {
        String apiKey = secretKey == null ? null
                : SecretCryptoUtil.decrypt(secretKey, request.model().getApiKeyEncrypted());
        if (apiKey == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), KEY_FAILURE_REASON);
        }
        long start = System.currentTimeMillis();
        try {
            ChatModel model = buildStreamChatModel(request, apiKey,
                    toolDefinitions == null ? List.of() : toolDefinitions);
            AiChatReply reply = streamAndAggregate(model, buildPrompt(request, priorTurns), request.onTick());
            writeUsage(request, System.currentTimeMillis() - start, true, reply.tokensIn(), reply.tokensOut(),
                    null, reply.toolCalls().isEmpty() ? SCENE_ANSWER : request.promptScene());
            return reply;
        } catch (Exception e) {
            logFailure(request.promptScene(), e);
            int errorCode = ErrorCodeConstants.AI_MODEL_CALL_FAILED.code();
            writeUsage(request, System.currentTimeMillis() - start, false, 0, 0, errorCode, request.promptScene());
            throw toServiceException(e, errorCode);
        }
    }

    /**
     * 中性工具声明的单跳调用（任务域执行核，详设 4.2）：与助手循环共用同一声明通道——
     * 声明只序列化 name / description / paramsSchema（category 仅助手循环语义，声明通道不消费，
     * 以 QUERY 占位），执行权全程在 AgentExecutionRunner 服务端循环。
     * 记账与错误映射同 {@link #chatWithTools}；priorTurns 为循环内既往轮次（协议要求成对出现）。
     */
    public AiChatReply chatWithToolDeclarations(AiChatRequest request, List<ToolDeclaration> declarations,
            List<Message> priorTurns) {
        return chatWithToolDeclarations(request, declarations, priorTurns, true);
    }

    /** 中性任务工具通道；仅 assistant=true 时将纯文本末跳归因 assistant_answer。 */
    public AiChatReply chatWithToolDeclarations(AiChatRequest request, List<ToolDeclaration> declarations,
            List<Message> priorTurns, boolean assistant) {
        String apiKey = secretKey == null ? null
                : SecretCryptoUtil.decrypt(secretKey, request.model().getApiKeyEncrypted());
        if (apiKey == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), KEY_FAILURE_REASON);
        }
        long start = System.currentTimeMillis();
        try {
            ChatModel model = buildStreamChatModelWithDeclarations(request, apiKey, declarations);
            AiChatReply reply = streamAndAggregate(model, buildPrompt(request, priorTurns), request.onTick());
            writeUsage(request, System.currentTimeMillis() - start, true, reply.tokensIn(), reply.tokensOut(),
                    null, assistant && reply.toolCalls().isEmpty() ? SCENE_ANSWER : request.promptScene());
            return reply;
        } catch (Exception e) {
            logFailure(request.promptScene(), e);
            int errorCode = ErrorCodeConstants.AI_MODEL_CALL_FAILED.code();
            writeUsage(request, System.currentTimeMillis() - start, false, 0, 0, errorCode, request.promptScene());
            throw toServiceException(e, errorCode);
        }
    }

    /**
     * 流式生成（助手详设 3.5 delta 通道）：逐段回调增量并返回聚合结果；记账与错误映射同 chat，
     * 成功 / 失败各记一条用量明细，token 以响应账单为准（未返回记 0）。
     */
    public AiChatReply streamChat(AiChatRequest request, Consumer<String> onDelta) {
        String apiKey = secretKey == null ? null
                : SecretCryptoUtil.decrypt(secretKey, request.model().getApiKeyEncrypted());
        if (apiKey == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), KEY_FAILURE_REASON);
        }
        long start = System.currentTimeMillis();
        try {
            ChatModel model = buildStreamChatModel(request, apiKey);
            AiChatReply reply = streamAndAggregate(model, buildPrompt(request), request.onTick(), onDelta);
            writeUsage(request, System.currentTimeMillis() - start, true, reply.tokensIn(), reply.tokensOut(),
                    null);
            return reply;
        } catch (Exception e) {
            logFailure(request.promptScene(), e);
            int errorCode = ErrorCodeConstants.AI_MODEL_CALL_FAILED.code();
            writeUsage(request, System.currentTimeMillis() - start, false, 0, 0, errorCode);
            throw toServiceException(e, errorCode);
        }
    }

    /**
     * 流式调用聚合（详设 4.2）：以首 token / 空闲双层超时订阅响应流，逐段累积文本与账单，
     * 工具调用取末帧完整列表（Spring AI 内部按 choice 累积，逐帧拼接会重复）。onDelta 逐段推送，
     * onTick 按固定间隔刷新任务心跳，防止长流期间被心跳清扫误杀。
     */
    private AiChatReply streamAndAggregate(ChatModel model, Prompt prompt, Runnable onTick) {
        return streamAndAggregate(model, prompt, onTick, text -> { });
    }

    private AiChatReply streamAndAggregate(ChatModel model, Prompt prompt, Runnable onTick,
            Consumer<String> onDelta) {
        StringBuilder content = new StringBuilder();
        List<AiToolCall>[] toolCalls = new List[]{List.of()};
        int[] tokensIn = {0};
        int[] tokensOut = {0};
        int[] totalTokens = {0};
        String[] finishReason = {null};
        long[] lastTick = {System.nanoTime()};
        Runnable tick = onTick == null ? () -> { } : onTick;
        model.stream(prompt)
                // 首 token 用 FIRST_TOKEN_TIMEOUT，其后每个 chunk 用 IDLE_TIMEOUT 重置计时
                .timeout(reactor.core.publisher.Mono.delay(firstTokenTimeout),
                        chunk -> reactor.core.publisher.Mono.delay(idleTimeout))
                .doOnNext(chunk -> {
                    String text = textOf(chunk);
                    if (text != null && !text.isEmpty()) {
                        content.append(text);
                        onDelta.accept(text);
                    }
                    List<AiToolCall> calls = toolCallsOf(chunk);
                    if (!calls.isEmpty()) {
                        toolCalls[0] = calls;
                    }
                    Usage usage = chunk.getMetadata() == null ? null : chunk.getMetadata().getUsage();
                    if (usage != null) {
                        if (usage.getPromptTokens() != null) {
                            tokensIn[0] = usage.getPromptTokens();
                        }
                        if (usage.getCompletionTokens() != null) {
                            tokensOut[0] = usage.getCompletionTokens();
                        }
                        if (usage.getTotalTokens() != null) {
                            totalTokens[0] = usage.getTotalTokens();
                        }
                    }
                    String reason = finishReasonOf(chunk);
                    if (reason != null && !reason.isBlank()) {
                        finishReason[0] = reason;
                    }
                    long now = System.nanoTime();
                    if (java.time.Duration.ofNanos(now - lastTick[0]).compareTo(streamHeartbeatInterval) >= 0) {
                        tick.run();
                        lastTick[0] = now;
                    }
                })
                .blockLast();
        String fullContent = content.toString();
        return new AiChatReply(fullContent, tokensIn[0], tokensOut[0], toolCalls[0],
                rawResponse(fullContent, toolCalls[0], finishReason[0], tokensIn[0], tokensOut[0], totalTokens[0]));
    }

    /** 单帧工具调用抽取：仅末帧携带完整列表，供 streamAndAggregate 覆盖式累积 */
    private static List<AiToolCall> toolCallsOf(ChatResponse chunk) {
        try {
            List<AssistantMessage.ToolCall> calls = chunk.getResult().getOutput().getToolCalls();
            if (calls == null || calls.isEmpty()) {
                return List.of();
            }
            return calls.stream()
                    .map(call -> new AiToolCall(call.id(), call.name(), call.arguments()))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String textOf(ChatResponse chunk) {
        try {
            String text = chunk.getResult().getOutput().getText();
            return text == null || text.isEmpty() ? null : text;
        } catch (Exception e) {
            return null;
        }
    }

    /** 完整响应原文随轨迹留存以支持审计回放（详设 4.2），账单与正文同源 */
    private static AiRawResponse rawResponse(String content, List<AiToolCall> toolCalls, String finishReason,
            int tokensIn, int tokensOut, int totalTokens) {
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("promptTokens", tokensIn);
        usage.put("completionTokens", tokensOut);
        usage.put("totalTokens", totalTokens);
        return new AiRawResponse(content, toolCalls, finishReason, usage);
    }

    /** 结束原因取不到时留空，避免模型元数据差异中断主调用 */
    private static String finishReasonOf(ChatResponse response) {
        try {
            return response.getResult().getMetadata().getFinishReason();
        } catch (Exception e) {
            return null;
        }
    }

    /** 按行动态配置程序式构建（配置在 DB，不走 starter 自动装配）；maxRetries 随当前设置注入 */
    ChatModel buildChatModel(AiChatRequest request, String apiKey) {
        return buildChatModel(request, apiKey, List.of());
    }

    /** 带工具声明的构建变体（工具清单来自服务端固化注册表，详设 4.1） */
    ChatModel buildChatModel(AiChatRequest request, String apiKey,
            List<AssistantToolDefinition> toolDefinitions) {
        List<ToolDeclaration> declarations = toolDefinitions == null ? List.of()
                : toolDefinitions.stream()
                        .map(item -> new ToolDeclaration(item.name(), item.description(), item.paramsSchema()))
                        .toList();
        return buildChatModelWithDeclarations(request, apiKey, declarations);
    }

    ChatModel buildChatModelWithDeclarations(AiChatRequest request, String apiKey,
            List<ToolDeclaration> declarations) {
        return OpenAiChatModel.builder()
                .options(buildOptionsWithDeclarations(request.model(), apiKey,
                        settingsReader.settings().taskMaxRetries(), declarations, request.jsonMode()))
                .build();
    }

    /** 流式构建：底层读取超时改为远大于首 token 护栏的值，实际终止由首 token / 空闲护栏接管（详设 4.2） */
    ChatModel buildStreamChatModel(AiChatRequest request, String apiKey) {
        return OpenAiChatModel.builder()
                .options(buildStreamOptionsWithDeclarations(request.model(), apiKey,
                        settingsReader.settings().taskMaxRetries(), List.of(), request.jsonMode()))
                .build();
    }

    ChatModel buildStreamChatModel(AiChatRequest request, String apiKey,
            List<AssistantToolDefinition> toolDefinitions) {
        List<ToolDeclaration> declarations = toolDefinitions == null ? List.of()
                : toolDefinitions.stream()
                        .map(item -> new ToolDeclaration(item.name(), item.description(), item.paramsSchema()))
                        .toList();
        return buildStreamChatModelWithDeclarations(request, apiKey, declarations);
    }

    ChatModel buildStreamChatModelWithDeclarations(AiChatRequest request, String apiKey,
            List<ToolDeclaration> declarations) {
        return OpenAiChatModel.builder()
                .options(buildStreamOptionsWithDeclarations(request.model(), apiKey,
                        settingsReader.settings().taskMaxRetries(), declarations, request.jsonMode()))
                .build();
    }

    /** 程序式构建调用选项：baseUrl / 密钥 / 模型名逐行取值，重试次数承接 task_max_retries（4.2） */
    static OpenAiChatOptions buildOptions(AiModelConfig model, String apiKey, int maxRetries) {
        return buildOptions(model, apiKey, maxRetries, List.of(), false);
    }

    /** 工具定义非空时以声明型回调随请求携带（详设 4.1：服务端固化清单，执行权不交框架） */
    static OpenAiChatOptions buildOptions(AiModelConfig model, String apiKey, int maxRetries,
            List<AssistantToolDefinition> toolDefinitions) {
        return buildOptions(model, apiKey, maxRetries, toolDefinitions, false);
    }

    static OpenAiChatOptions buildOptions(AiModelConfig model, String apiKey, int maxRetries,
            List<AssistantToolDefinition> toolDefinitions, boolean jsonMode) {
        List<ToolDeclaration> declarations = toolDefinitions == null ? List.of()
                : toolDefinitions.stream()
                        .map(item -> new ToolDeclaration(item.name(), item.description(), item.paramsSchema()))
                        .toList();
        return buildOptionsWithDeclarations(model, apiKey, maxRetries, declarations, jsonMode);
    }

    private static OpenAiChatOptions buildOptionsWithDeclarations(AiModelConfig model, String apiKey, int maxRetries,
            List<ToolDeclaration> declarations, boolean jsonMode) {
        return buildOptionsInternal(model, apiKey, maxRetries, declarations, REQUEST_TIMEOUT, jsonMode);
    }

    /**
     * 流式选项：底层读取超时放到远大于首 token 护栏的值（Spring AI builder 缺省 60s 会掐断长流），
     * 真正的首 token / 空闲终止交由 Reactor 护栏（详设 4.2）。
     */
    private static OpenAiChatOptions buildStreamOptionsWithDeclarations(AiModelConfig model, String apiKey,
            int maxRetries, List<ToolDeclaration> declarations, boolean jsonMode) {
        return buildOptionsInternal(model, apiKey, maxRetries, declarations, STREAM_UNDERLYING_TIMEOUT, jsonMode);
    }

    private static OpenAiChatOptions buildOptionsInternal(AiModelConfig model, String apiKey, int maxRetries,
            List<ToolDeclaration> declarations, Duration timeout, boolean jsonMode) {
        var builder = OpenAiChatOptions.builder()
                .baseUrl(model.getBaseUrl())
                .apiKey(apiKey)
                .model(model.getModelName())
                .maxRetries(maxRetries);
        if (timeout != null) {
            builder.timeout(timeout);
        }
        if (declarations != null && !declarations.isEmpty()) {
            builder.toolCallbacks(declarationCallbacks(declarations));
        }
        if (jsonMode) {
            builder.responseFormat(OpenAiChatModel.ResponseFormat.builder()
                    .type(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT)
                    .build());
        }
        return builder.build();
    }

    private static List<ToolCallback> declarationCallbacks(List<ToolDeclaration> toolDefinitions) {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (ToolDeclaration definition : toolDefinitions) {
            callbacks.add(new DeclarationCallback(definition));
        }
        return callbacks;
    }

    /**
     * 声明型工具回调：只把 name / description / paramsSchema 序列化进请求（详设 4.1），
     * call() 永不触达——OpenAiChatModel 不执行工具，服务端循环才是唯一执行方；触达即为缺陷。
     */
    private static final class DeclarationCallback implements ToolCallback {

        private final ToolDefinition definition;

        private DeclarationCallback(ToolDeclaration source) {
            this.definition = DefaultToolDefinition.builder()
                    .name(source.name())
                    .description(source.description())
                    .inputSchema(JsonUtils.toJsonString(source.paramsSchema()))
                    .build();
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public String call(String toolInput) {
            throw new IllegalStateException("工具执行由服务端循环接管，声明型回调不可执行: " + definition.name());
        }
    }

    static Prompt buildPrompt(AiChatRequest request) {
        return buildPrompt(request, List.of());
    }

    /** 携带循环内既往轮次（详设 4.1：assistant tool_calls 与 tool 回填消息追加在 system / user 之后） */
    static Prompt buildPrompt(AiChatRequest request, List<Message> priorTurns) {
        List<Message> messages = new ArrayList<>();
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            messages.add(new SystemMessage(request.systemPrompt()));
        }
        List<Media> medias = toMedias(request);
        if (medias.isEmpty()) {
            messages.add(new UserMessage(request.userPrompt()));
        } else {
            messages.add(UserMessage.builder().text(request.userPrompt()).media(medias).build());
        }
        if (priorTurns != null && !priorTurns.isEmpty()) {
            messages.addAll(priorTurns);
        }
        return new Prompt(messages);
    }

    /** 渲染随行媒体列表（详设 4.2 多模态直传）：字节为空的条目跳过 */
    private static List<Media> toMedias(AiChatRequest request) {
        return request.media().stream()
                .filter(AiModelClient::hasBytes)
                .map(media -> Media.builder()
                        .mimeType(MimeTypeUtils.parseMimeType(media.mimeType()))
                        .data(media.data())
                        .build())
                .toList();
    }

    /** 媒体非空判定：构建提示词与用量 call_type=vision（详设 3.7 / 裁决 D5）共用同一口径 */
    private static boolean hasMedia(AiChatRequest request) {
        return request.media().stream().anyMatch(AiModelClient::hasBytes);
    }

    private static boolean hasBytes(AiChatMedia media) {
        return media != null && media.data() != null && media.data().length > 0;
    }

    private static AiChatReply extractReply(ChatResponse response) {
        try {
            String content = String.valueOf(response.getResult().getOutput().getText());
            Usage usage = response.getMetadata().getUsage();
            int tokensIn = usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
            int tokensOut = usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
            int totalTokens = usage == null || usage.getTotalTokens() == null ? tokensIn + tokensOut
                    : usage.getTotalTokens();
            return new AiChatReply(content, tokensIn, tokensOut, List.of(),
                    rawResponse(content, List.of(), finishReasonOf(response), tokensIn, tokensOut, totalTokens));
        } catch (Exception e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(),
                    AiFailureReason.of(e, ERROR_MSG_MAX_LENGTH));
        }
    }

    /** 带工具调用载荷的回复抽取：工具与正文、账单同源（详设 4.1 单跳结果三要素） */
    private static AiChatReply extractReplyWithCalls(ChatResponse response) {
        try {
            AssistantMessage output = response.getResult().getOutput();
            String content = String.valueOf(output.getText());
            List<AiToolCall> toolCalls = output.getToolCalls() == null ? List.of()
                    : output.getToolCalls().stream()
                            .map(call -> new AiToolCall(call.id(), call.name(), call.arguments()))
                            .toList();
            Usage usage = response.getMetadata().getUsage();
            int tokensIn = usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
            int tokensOut = usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
            int totalTokens = usage == null || usage.getTotalTokens() == null ? tokensIn + tokensOut
                    : usage.getTotalTokens();
            return new AiChatReply(content, tokensIn, tokensOut, toolCalls,
                    rawResponse(content, toolCalls, finishReasonOf(response), tokensIn, tokensOut, totalTokens));
        } catch (Exception e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(),
                    AiFailureReason.of(e, ERROR_MSG_MAX_LENGTH));
        }
    }

    private static ServiceException toServiceException(Exception e, int errorCode) {
        if (e instanceof ServiceException se && se.getCode() != null && se.getCode() == errorCode) {
            return se;
        }
        return ServiceExceptionUtil.get(errorCode, AiFailureReason.of(e, ERROR_MSG_MAX_LENGTH));
    }

    /** 失败日志只记录异常类型与脱敏原因，不打印原始异常对象（详设总册 4.5） */
    private static void logFailure(String scene, Exception e) {
        log.warn("[AI] 模型调用失败 scene={} type={} reason={}", scene, e.getClass().getSimpleName(),
                AiFailureReason.of(e));
    }

    /** 每次逻辑调用记一条用量明细；记录失败只告警，不中断模型调用（4.3） */
    private void writeUsage(AiChatRequest request, long latencyMs, boolean success, int promptTokens,
            int completionTokens, Integer errorCode) {
        writeUsage(request, latencyMs, success, promptTokens, completionTokens, errorCode, request.promptScene());
    }

    /** scene 落账口径由调用方决定（工具跳按结果落账，详设 4.3 助手交互调用归因） */
    private void writeUsage(AiChatRequest request, long latencyMs, boolean success, int promptTokens,
            int completionTokens, Integer errorCode, String promptScene) {
        try {
            AiUsageLog usage = new AiUsageLog();
            usage.setProjectId(request.projectId());
            usage.setTaskId(request.taskId());
            usage.setExecutionId(request.executionId());
            usage.setUserId(request.userId());
            usage.setModelId(request.model().getId());
            usage.setPromptScene(promptScene);
            // 带图调用记 vision（详设 3.7 / 裁决 D5）：用量分析按模态下钻，无 DDL
            usage.setCallType(hasMedia(request) ? "vision" : "chat");
            usage.setPromptTokens(promptTokens);
            usage.setCompletionTokens(completionTokens);
            usage.setTotalTokens(promptTokens + completionTokens);
            usage.setLatencyMs((int) Math.min(latencyMs, Integer.MAX_VALUE));
            usage.setStatus(success ? "success" : "failed");
            usage.setErrorCode(errorCode);
            usage.setCost(calculateCost(request.model(), promptTokens, completionTokens));
            usageLogMapper.insert(usage);
        } catch (Exception e) {
            log.warn("[AI] 用量记录写入失败 taskId={} scene={} type={} reason={}", request.taskId(),
                    request.promptScene(), e.getClass().getSimpleName(), AiFailureReason.of(e));
        }
    }

    /** 成本 = prompt_tokens / 1e6 × input_price + completion_tokens / 1e6 × output_price（4.3；单价未配置恒 0） */
    public static BigDecimal calculateCost(AiModelConfig model, int promptTokens, int completionTokens) {
        BigDecimal inputPrice = model.getInputPrice() == null ? BigDecimal.ZERO : model.getInputPrice();
        BigDecimal outputPrice = model.getOutputPrice() == null ? BigDecimal.ZERO : model.getOutputPrice();
        BigDecimal million = new BigDecimal("1000000");
        return inputPrice.multiply(new BigDecimal(promptTokens)).divide(million, 6, RoundingMode.HALF_UP)
                .add(outputPrice.multiply(new BigDecimal(completionTokens)).divide(million, 6, RoundingMode.HALF_UP));
    }
}
