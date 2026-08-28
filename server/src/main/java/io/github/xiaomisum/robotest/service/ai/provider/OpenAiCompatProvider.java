package io.github.xiaomisum.robotest.service.ai.provider;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ChatCallOptions;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ChatMessage;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ChatResult;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.EmbedResult;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.StreamCallbacks;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ToolCall;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ToolDefinition;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ToolStreamCallbacks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.util.JsonUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenAI 兼容协议 HTTP 客户端 —— 唯一 Provider 实现。
 *
 * <p>同步调用直接绑定响应体；流式调用经 exchange 直读响应字节流逐行解析 SSE，
 * 阻塞读取由虚拟线程承载（平台已全局启用虚拟线程）。供应商差异只体现在配置层
 * （默认地址与 extraParams），装配路径对全部供应商一致。</p>
 */
@Slf4j
@Component
public class OpenAiCompatProvider {

    private static final int CONNECT_TIMEOUT_MILLIS = 3_000;
    private static final int SYNC_READ_TIMEOUT_MILLIS = 15_000;
    /** 流式读超时按帧间 60s 设置（HttpURLConnection 的 readTimeout 作用于单次阻塞读）；
     * 首帧 10s 目标由 SSE 接口总超时兜底 */
    private static final int STREAM_READ_TIMEOUT_MILLIS = 60_000;
    private static final int EMBED_READ_TIMEOUT_MILLIS = 10_000;
    /** 无法解析的上游 SSE 帧超过该阈值判定上游异常 */
    private static final int BAD_FRAME_THRESHOLD = 20;

    /**
     * 同步对话调用（网络/5xx 自动重试 1 次，401/403 不重试）
     */
    public ChatResult complete(ResolvedChatModel config, List<ChatMessage> messages, ChatCallOptions options) {
        Map<String, Object> body = buildChatBody(config, messages, options, false);
        int readTimeout = options.readTimeoutMillis() != null ? options.readTimeoutMillis() : SYNC_READ_TIMEOUT_MILLIS;
        JsonNode root = postWithRetry(config.baseUrl(), "/chat/completions", config.apiKey(), body, readTimeout);

        JsonNode choice = root.path("choices").path(0);
        String content = choice.path("message").path("content").asString(null);
        return new ChatResult(content,
                intOrNull(root.path("usage").path("prompt_tokens")),
                intOrNull(root.path("usage").path("completion_tokens")),
                choice.path("finish_reason").asString(null));
    }

    /**
     * 流式对话调用（不自动重试）；cancelled 置位后在下一行读取边界退出并抛出 StreamCancelledException
     */
    public void stream(ResolvedChatModel config, List<ChatMessage> messages, ChatCallOptions options,
                       StreamCallbacks callbacks, AtomicBoolean cancelled) {
        Map<String, Object> body = buildChatBody(config, messages, options, true);
        RestClient client = buildClient(config.baseUrl(), STREAM_READ_TIMEOUT_MILLIS);

        client.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + config.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(JsonUtils.toJsonString(body))
                .exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        log.warn("[AI] 流式调用上游返回 {}", response.getStatusCode());
                        throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_CALL_FAILED);
                    }
                    StringBuilder fullContent = new StringBuilder();
                    Integer promptTokens = null;
                    Integer completionTokens = null;
                    int badFrames = 0;
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (cancelled.get()) {
                                throw new StreamCancelledException();
                            }
                            if (!line.startsWith("data:")) {
                                continue;
                            }
                            String data = line.substring(5).trim();
                            if (data.isEmpty()) {
                                continue;
                            }
                            if ("[DONE]".equals(data)) {
                                break;
                            }
                            JsonNode chunk;
                            try {
                                chunk = JsonUtils.toJSON(data);
                            } catch (Exception e) {
                                if (++badFrames > BAD_FRAME_THRESHOLD) {
                                    throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_CALL_FAILED);
                                }
                                continue;
                            }
                            String delta = chunk.path("choices").path(0).path("delta").path("content").asString(null);
                            if (delta != null && !delta.isEmpty()) {
                                fullContent.append(delta);
                                callbacks.onDelta(delta);
                            }
                            JsonNode usage = chunk.path("usage");
                            if (!usage.isMissingNode() && !usage.isNull()) {
                                promptTokens = intOrNull(usage.path("prompt_tokens"));
                                completionTokens = intOrNull(usage.path("completion_tokens"));
                            }
                        }
                    }
                    callbacks.onFinish(fullContent.toString(), promptTokens, completionTokens);
                    return null;
                });
    }

    /**
     * 带工具定义的流式对话调用（Function Calling）。
     * 在普通 stream 基础上解析 delta.tool_calls 增量帧，流式结束后回调 onToolCalls。
     */
    public void streamWithTools(ResolvedChatModel config, List<ChatMessage> messages, ChatCallOptions options,
                                ToolStreamCallbacks callbacks, AtomicBoolean cancelled) {
        Map<String, Object> body = buildChatBody(config, messages, options, true);
        RestClient client = buildClient(config.baseUrl(), STREAM_READ_TIMEOUT_MILLIS);

        client.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + config.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(JsonUtils.toJsonString(body))
                .exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        log.warn("[AI] 流式调用上游返回 {}", response.getStatusCode());
                        throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_CALL_FAILED);
                    }
                    StringBuilder fullContent = new StringBuilder();
                    Integer promptTokens = null;
                    Integer completionTokens = null;
                    int badFrames = 0;
                    // tool_calls 增量累积：index → (id, name, argumentsBuilder)
                    List<ToolCallAccumulator> toolCallAccumulators = new ArrayList<>();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (cancelled.get()) {
                                throw new StreamCancelledException();
                            }
                            if (!line.startsWith("data:")) {
                                continue;
                            }
                            String data = line.substring(5).trim();
                            if (data.isEmpty()) {
                                continue;
                            }
                            if ("[DONE]".equals(data)) {
                                break;
                            }
                            JsonNode chunk;
                            try {
                                chunk = JsonUtils.toJSON(data);
                            } catch (Exception e) {
                                if (++badFrames > BAD_FRAME_THRESHOLD) {
                                    throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_CALL_FAILED);
                                }
                                continue;
                            }
                            JsonNode delta = chunk.path("choices").path(0).path("delta");
                            // 文本增量
                            String contentDelta = delta.path("content").asString(null);
                            if (contentDelta != null && !contentDelta.isEmpty()) {
                                fullContent.append(contentDelta);
                                callbacks.onDelta(contentDelta);
                            }
                            // tool_calls 增量
                            JsonNode toolCallsNode = delta.path("tool_calls");
                            if (!toolCallsNode.isMissingNode() && toolCallsNode.isArray()) {
                                for (JsonNode tc : toolCallsNode) {
                                    int index = tc.path("index").asInt(0);
                                    // 扩展 accumulator 列表
                                    while (toolCallAccumulators.size() <= index) {
                                        toolCallAccumulators.add(new ToolCallAccumulator());
                                    }
                                    ToolCallAccumulator acc = toolCallAccumulators.get(index);
                                    String id = tc.path("id").asString(null);
                                    if (id != null) {
                                        acc.id = id;
                                    }
                                    String name = tc.path("function").path("name").asString(null);
                                    if (name != null) {
                                        acc.name = name;
                                    }
                                    String argsPart = tc.path("function").path("arguments").asString(null);
                                    if (argsPart != null) {
                                        acc.argumentsBuilder.append(argsPart);
                                    }
                                }
                            }
                            JsonNode usage = chunk.path("usage");
                            if (!usage.isMissingNode() && !usage.isNull()) {
                                promptTokens = intOrNull(usage.path("prompt_tokens"));
                                completionTokens = intOrNull(usage.path("completion_tokens"));
                            }
                        }
                    }
                    // 构建工具调用列表
                    List<ToolCall> toolCalls = toolCallAccumulators.stream()
                            .filter(acc -> acc.id != null && acc.name != null)
                            .map(acc -> {
                                Map<String, Object> args;
                                try {
                                    String argsJson = acc.argumentsBuilder.toString();
                                    args = (argsJson.isEmpty() || "{}".equals(argsJson))
                                            ? Map.of()
                                            : JsonUtils.parseObject(argsJson,
                                                    new tools.jackson.core.type.TypeReference<Map<String, Object>>() {});
                                } catch (Exception e) {
                                    log.warn("[AI] tool_calls arguments 解析失败: {}", e.getMessage());
                                    args = Map.of();
                                }
                                return new ToolCall(acc.id, acc.name, args);
                            })
                            .toList();
                    callbacks.onToolCalls(toolCalls);
                    callbacks.onFinish(fullContent.toString(), promptTokens, completionTokens);
                    return null;
                });
    }

    /**
     * Embedding 调用（网络/5xx 自动重试 1 次）
     */
    public EmbedResult embed(ResolvedAiConfig config, List<String> inputs) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.embeddingModel());
        body.put("input", inputs);
        if (config.embeddingDimension() != null) {
            body.put("dimensions", config.embeddingDimension());
        }
        mergeExtraParams(body, config.embeddingExtraParams(), ProviderPresetRegistry.EMBEDDING_STANDARD_PARAMS);

        JsonNode root = postWithRetry(config.embeddingBaseUrl(), "/embeddings", config.embeddingApiKey(),
                body, EMBED_READ_TIMEOUT_MILLIS);
        List<float[]> vectors = new ArrayList<>();
        for (JsonNode item : root.path("data")) {
            JsonNode embedding = item.path("embedding");
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = (float) embedding.get(i).asDouble();
            }
            vectors.add(vector);
        }
        return new EmbedResult(vectors, intOrNull(root.path("usage").path("prompt_tokens")));
    }

    private Map<String, Object> buildChatBody(ResolvedChatModel config, List<ChatMessage> messages,
                                              ChatCallOptions options, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());
        // 序列化消息：支持 tool role / assistant tool_calls / tools 数组
        body.put("messages", messages.stream()
                .map(this::serializeMessage)
                .toList());
        body.put("stream", stream);
        if (options.maxTokens() != null) {
            body.put("max_tokens", options.maxTokens());
        }
        if (options.temperature() != null) {
            body.put("temperature", options.temperature());
        }
        if (options.jsonResponseFormat()) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        // Function Calling 工具定义
        if (options.tools() != null && !options.tools().isEmpty()) {
            body.put("tools", options.tools().stream()
                    .map(tool -> Map.of("type", "function",
                            "function", Map.of(
                                    "name", tool.name(),
                                    "description", tool.description(),
                                    "parameters", tool.parameters())))
                    .toList());
        }
        mergeExtraParams(body, config.extraParams(), ProviderPresetRegistry.CHAT_STANDARD_PARAMS);
        return body;
    }

    /**
     * 消息序列化：按 role 区分结构，支持 tool_calls / tool_call_id
     */
    private Map<String, Object> serializeMessage(ChatMessage message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", message.role());
        if (message.content() != null) {
            map.put("content", message.content());
        }
        // assistant 消息携带 tool_calls
        if ("assistant".equals(message.role()) && message.toolCalls() != null && !message.toolCalls().isEmpty()) {
            map.put("tool_calls", message.toolCalls().stream()
                    .map(tc -> Map.of("id", tc.id(),
                            "type", "function",
                            "function", Map.of(
                                    "name", tc.name(),
                                    "arguments", serializeArguments(tc.arguments()))))
                    .toList());
        }
        // tool 消息携带 tool_call_id
        if ("tool".equals(message.role()) && message.toolCallId() != null) {
            map.put("tool_call_id", message.toolCallId());
        }
        return map;
    }

    /**
     * 工具参数序列化：Map → JSON 字符串（OpenAI 协议要求 arguments 为 string）
     */
    private String serializeArguments(Map<String, Object> arguments) {
        if (arguments == null) {
            return "{}";
        }
        try {
            return JsonUtils.toJsonString(arguments);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * extraParams 在白名单参数装配之后浅合并：白名单键不可被覆盖，其余键原样透传
     */
    private void mergeExtraParams(Map<String, Object> body, Map<String, Object> extraParams, Set<String> whitelist) {
        if (extraParams == null) {
            return;
        }
        extraParams.forEach((key, value) -> {
            if (!whitelist.contains(key)) {
                body.put(key, value);
            }
        });
    }

    private JsonNode postWithRetry(String baseUrl, String path, String apiKey,
                                   Map<String, Object> body, int readTimeoutMillis) {
        try {
            return postOnce(baseUrl, path, apiKey, body, readTimeoutMillis);
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            // 上游鉴权错误不重试，直接失败并在管理端统计可见
            if (status.value() == 401 || status.value() == 403 || !status.is5xxServerError()) {
                log.warn("[AI] 上游调用失败 status={} body={}", status.value(), truncate(e.getResponseBodyAsString()));
                throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_CALL_FAILED);
            }
        } catch (StreamCancelledException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[AI] 上游调用异常: {}", e.getMessage());
        }
        try {
            return postOnce(baseUrl, path, apiKey, body, readTimeoutMillis);
        } catch (Exception e) {
            log.warn("[AI] 上游调用重试仍失败: {}", e.getMessage());
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_CALL_FAILED);
        }
    }

    private JsonNode postOnce(String baseUrl, String path, String apiKey,
                              Map<String, Object> body, int readTimeoutMillis) {
        RestClient client = buildClient(baseUrl, readTimeoutMillis);
        String response = client.post()
                .uri(path)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(JsonUtils.toJsonString(body))
                .retrieve()
                .body(String.class);
        return JsonUtils.toJSON(response);
    }

    private RestClient buildClient(String baseUrl, int readTimeoutMillis) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        factory.setReadTimeout(readTimeoutMillis);
        return RestClient.builder()
                .baseUrl(trimTrailingSlash(baseUrl))
                .requestFactory(factory)
                .build();
    }

    private String trimTrailingSlash(String baseUrl) {
        return baseUrl != null && baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private Integer intOrNull(JsonNode node) {
        return node.isNumber() ? node.asInt() : null;
    }

    private String truncate(String text) {
        return text != null && text.length() > 200 ? text.substring(0, 200) : text;
    }

    /** 客户端断开触发的流式取消（协作式，行读取边界生效） */
    public static class StreamCancelledException extends RuntimeException {
    }

    /** tool_calls 增量累积器（按 index 分组，arguments 分片拼接） */
    private static class ToolCallAccumulator {
        String id;
        String name;
        StringBuilder argumentsBuilder = new StringBuilder();
    }
}
