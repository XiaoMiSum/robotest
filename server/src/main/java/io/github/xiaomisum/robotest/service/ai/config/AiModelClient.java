package io.github.xiaomisum.robotest.service.ai.config;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiUsageLog;
import io.github.xiaomisum.robotest.repository.ai.AiUsageLogMapper;
import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
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

/**
 * OpenAI 兼容模型客户端（详设 4.2 / 4.3）：
 * 调用失败按 task_max_retries 同模型自动短重试，每次尝试（成功与失败）各记一条 ai_usage_log；
 * 模型服务不可用 / 超时 / 限流耗尽后抛 1000018117。序列化与解析走 JsonUtils，规避消息转换器差异。
 */
@Component
public class AiModelClient {

    private static final Logger log = LoggerFactory.getLogger(AiModelClient.class);

    /** 连接快速失败：不可达端点不应长期占用执行线程 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    /** 单次生成读超时：兜底长挂起，整体仍受任务超时清扫（4.2）约束 */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(120);
    private static final int ERROR_MSG_MAX_LENGTH = 500;

    private final RestClient restClient;
    /** 平台业务加密密钥（SecretCryptoUtil 多域共用，4.1 沿用既有机制），未配置时解密失败降级 1000018117 */
    private final byte[] secretKey;

    @Resource
    private AiUsageLogMapper usageLogMapper;
    @Resource
    private AiSettingsReader settingsReader;

    public AiModelClient(@Value("${robotest.env.secret-key:}") String base64SecretKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.secretKey = SecretCryptoUtil.parseKey(base64SecretKey);
    }

    public AiChatReply chat(AiChatRequest request) {
        String apiKey = secretKey == null ? null
                : SecretCryptoUtil.decrypt(secretKey, request.model().getApiKeyEncrypted());
        if (apiKey == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED);
        }
        String url = join(request.model().getBaseUrl(), "chat/completions");
        Map<String, Object> body = buildBody(request);
        int attempts = 1 + settingsReader.settings().taskMaxRetries();
        ServiceException last = null;
        for (int attempt = 0; attempt < attempts; attempt++) {
            long start = System.currentTimeMillis();
            try {
                String raw = restClient.post()
                        .uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(JsonUtils.toJsonString(body))
                        .retrieve()
                        .body(String.class);
                Map<String, Object> response = JsonUtils.parseObject(raw, Map.class);
                AiChatReply reply = extractReply(response);
                writeUsage(request, System.currentTimeMillis() - start, true, reply.tokensIn(), reply.tokensOut(),
                        null);
                return reply;
            } catch (Exception e) {
                int errorCode = ErrorCodeConstants.AI_MODEL_CALL_FAILED.code();
                writeUsage(request, System.currentTimeMillis() - start, false, 0, 0, errorCode);
                last = toServiceException(e, errorCode);
            }
        }
        throw last == null ? ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED) : last;
    }

    private static Map<String, Object> buildBody(AiChatRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        }
        messages.add(Map.of("role", "user", "content", request.userPrompt()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model().getModelName());
        body.put("messages", messages);
        body.put("stream", false);
        return body;
    }

    @SuppressWarnings("unchecked")
    private static AiChatReply extractReply(Map<String, Object> response) {
        try {
            List<Object> choices = (List<Object>) response.get("choices");
            Map<String, Object> message = (Map<String, Object>) ((Map<String, Object>) choices.get(0)).get("message");
            String content = String.valueOf(message.get("content"));
            Map<String, Object> usage = (Map<String, Object>) response.get("usage");
            int tokensIn = usage == null ? 0 : ((Number) usage.getOrDefault("prompt_tokens", 0)).intValue();
            int tokensOut = usage == null ? 0 : ((Number) usage.getOrDefault("completion_tokens", 0)).intValue();
            return new AiChatReply(content, tokensIn, tokensOut);
        } catch (Exception e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED);
        }
    }

    private static ServiceException toServiceException(Exception e, int errorCode) {
        if (e instanceof ServiceException se && se.getCode() != null && se.getCode() == errorCode) {
            return se;
        }
        String message = e.getMessage() == null ? ErrorCodeConstants.AI_MODEL_CALL_FAILED.msg() : e.getMessage();
        if (message.length() > ERROR_MSG_MAX_LENGTH) {
            message = message.substring(0, ERROR_MSG_MAX_LENGTH);
        }
        return ServiceExceptionUtil.get(errorCode, message);
    }

    /** 每次调用（成功与失败）记一条用量明细；记录失败只告警，不中断模型调用（4.3） */
    private void writeUsage(AiChatRequest request, long latencyMs, boolean success, int promptTokens,
            int completionTokens, Integer errorCode) {
        try {
            AiUsageLog usage = new AiUsageLog();
            usage.setProjectId(request.projectId());
            usage.setTaskId(request.taskId());
            usage.setUserId(request.userId());
            usage.setModelId(request.model().getId());
            usage.setPromptScene(request.promptScene());
            usage.setCallType("chat");
            usage.setPromptTokens(promptTokens);
            usage.setCompletionTokens(completionTokens);
            usage.setTotalTokens(promptTokens + completionTokens);
            usage.setLatencyMs((int) Math.min(latencyMs, Integer.MAX_VALUE));
            usage.setStatus(success ? "success" : "failed");
            usage.setErrorCode(errorCode);
            usage.setCost(calculateCost(request.model(), promptTokens, completionTokens));
            usageLogMapper.insert(usage);
        } catch (Exception e) {
            log.warn("[AI] 用量记录写入失败 taskId={} scene={}", request.taskId(), request.promptScene(), e);
        }
    }

    /** 成本 = prompt_tokens / 1e6 × input_price + completion_tokens / 1e6 × output_price（4.3；单价未配置恒 0） */
    private static BigDecimal calculateCost(AiModelConfig model, int promptTokens, int completionTokens) {
        BigDecimal inputPrice = model.getInputPrice() == null ? BigDecimal.ZERO : model.getInputPrice();
        BigDecimal outputPrice = model.getOutputPrice() == null ? BigDecimal.ZERO : model.getOutputPrice();
        BigDecimal million = new BigDecimal("1000000");
        return inputPrice.multiply(new BigDecimal(promptTokens)).divide(million, 6, RoundingMode.HALF_UP)
                .add(outputPrice.multiply(new BigDecimal(completionTokens)).divide(million, 6, RoundingMode.HALF_UP));
    }

    private static String join(String baseUrl, String path) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + path;
    }
}
