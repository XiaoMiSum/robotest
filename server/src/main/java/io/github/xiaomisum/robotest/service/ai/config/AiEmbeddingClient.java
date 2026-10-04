package io.github.xiaomisum.robotest.service.ai.config;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import io.github.xiaomisum.robotest.model.entity.ai.AiEmbeddingConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiUsageLog;
import io.github.xiaomisum.robotest.repository.ai.AiUsageLogMapper;
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
import java.util.UUID;

/**
 * 向量 API 客户端（OpenAI 兼容 /embeddings）：供连通性测试（3.4）与向量重建（4.4）复用。
 * 每次调用（成功与失败）记一条 ai_usage_log（4.3）：call_type = embedding、model_id = NULL、cost = 0。
 */
@Component
public class AiEmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(AiEmbeddingClient.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);
    private static final int ERROR_MSG_MAX_LENGTH = 500;

    private final RestClient restClient;
    private final byte[] secretKey;

    @Resource
    private AiUsageLogMapper usageLogMapper;

    public AiEmbeddingClient(@Value("${robotest.env.secret-key:}") String base64SecretKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.secretKey = SecretCryptoUtil.parseKey(base64SecretKey);
    }

    /**
     * 文本向量化（取首个向量）。失败抛 1000018117（连通性测试场景由调用方转 1000018107）。
     *
     * @param userId  记账操作人（重嵌场景为任务提交人）
     * @param taskId  任务归属（测试为 NULL）
     */
    public EmbeddingReply embed(AiEmbeddingConfig config, List<String> inputs, UUID userId, UUID taskId,
            UUID projectId) {
        EmbeddingsReply batch = doEmbed(config, inputs, userId, taskId, projectId, false);
        List<Double> first = batch.vectors().getFirst();
        return new EmbeddingReply(first, first.size(), batch.tokens());
    }

    /**
     * 批量文本向量化（详设 4.4 分块重嵌复用同一次 HTTP）：严格校验响应数量与维度，
     * 防止错位 / 维度漂移的向量写进索引列（写入期才暴露会得到晦涩的 PG 转换错误）。
     */
    public EmbeddingsReply embedBatch(AiEmbeddingConfig config, List<String> inputs, UUID userId, UUID taskId,
            UUID projectId) {
        return doEmbed(config, inputs, userId, taskId, projectId, true);
    }

    private EmbeddingsReply doEmbed(AiEmbeddingConfig config, List<String> inputs, UUID userId, UUID taskId,
            UUID projectId, boolean strict) {
        String apiKey = secretKey == null ? null : SecretCryptoUtil.decrypt(secretKey, config.getApiKeyEncrypted());
        if (apiKey == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED);
        }
        String url = join(config.getBaseUrl(), "embeddings");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getEmbeddingModel());
        body.put("input", inputs);

        long start = System.currentTimeMillis();
        try {
            String raw = restClient.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(JsonUtils.toJsonString(body))
                    .retrieve()
                    .body(String.class);
            EmbeddingsReply reply = extractReply(raw, strict, inputs.size(), config.getDimensions());
            writeUsage(config, userId, taskId, projectId, System.currentTimeMillis() - start, true,
                    reply.tokens(), null);
            return reply;
        } catch (ServiceException e) {
            writeUsage(config, userId, taskId, projectId, System.currentTimeMillis() - start, false, 0,
                    ErrorCodeConstants.AI_MODEL_CALL_FAILED.code());
            throw e;
        } catch (Exception e) {
            writeUsage(config, userId, taskId, projectId, System.currentTimeMillis() - start, false, 0,
                    ErrorCodeConstants.AI_MODEL_CALL_FAILED.code());
            throw toServiceException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static EmbeddingsReply extractReply(String raw, boolean strict, int expectedCount,
            Integer expectedDimensions) {
        try {
            Map<String, Object> response = JsonUtils.parseObject(raw, Map.class);
            List<Object> data = (List<Object>) response.get("data");
            if (data == null || data.isEmpty()) {
                throw new IllegalArgumentException("向量响应缺少 data");
            }
            List<List<Double>> vectors = new ArrayList<>(data.size());
            for (Object item : data) {
                List<Object> vector = (List<Object>) ((Map<String, Object>) item).get("embedding");
                vectors.add(vector.stream().map(v -> ((Number) v).doubleValue()).toList());
            }
            Map<String, Object> usage = (Map<String, Object>) response.get("usage");
            int tokens = usage == null ? 0 : ((Number) usage.getOrDefault("prompt_tokens", 0)).intValue();
            if (strict) {
                if (vectors.size() != expectedCount) {
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(),
                            "向量响应数量与输入不一致");
                }
                for (List<Double> vector : vectors) {
                    if (expectedDimensions != null && vector.size() != expectedDimensions) {
                        throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(),
                                "向量维度与配置不一致（返回 " + vector.size() + "，配置 " + expectedDimensions + "）");
                    }
                }
            }
            return new EmbeddingsReply(List.copyOf(vectors), tokens);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), "向量响应解析失败");
        }
    }

    private void writeUsage(AiEmbeddingConfig config, UUID userId, UUID taskId, UUID projectId, long latencyMs,
            boolean success, int tokens, Integer errorCode) {
        try {
            AiUsageLog usage = new AiUsageLog();
            usage.setProjectId(projectId);
            usage.setTaskId(taskId);
            usage.setUserId(userId);
            usage.setCallType("embedding");
            usage.setPromptTokens(tokens);
            usage.setCompletionTokens(0);
            usage.setTotalTokens(tokens);
            usage.setLatencyMs((int) Math.min(latencyMs, Integer.MAX_VALUE));
            usage.setStatus(success ? "success" : "failed");
            usage.setErrorCode(errorCode);
            // 向量调用无模型配置行 → 无单价来源，cost 恒 0（详设 4.3 / 2.8 勘误）
            usage.setCost(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP));
            usageLogMapper.insert(usage);
        } catch (Exception e) {
            log.warn("[AI] 向量用量记录写入失败 taskId={} userId={}", taskId, userId, e);
        }
    }

    private static ServiceException toServiceException(Exception e) {
        String message = e.getMessage() == null ? ErrorCodeConstants.AI_MODEL_CALL_FAILED.msg() : e.getMessage();
        if (message.length() > ERROR_MSG_MAX_LENGTH) {
            message = message.substring(0, ERROR_MSG_MAX_LENGTH);
        }
        return ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), message);
    }

    private static String join(String baseUrl, String path) {
        return baseUrl.endsWith("/") ? baseUrl + path : baseUrl + "/" + path;
    }

    /** 向量化回复：dimensions 用于维度一致性校验（3.4 测试响应与 4.4 重建） */
    public record EmbeddingReply(List<Double> embedding, int dimensions, int tokens) {
    }

    /** 批量向量化回复：vectors 与输入一一对应（严格模式已校验数量与维度） */
    public record EmbeddingsReply(List<List<Double>> vectors, int tokens) {
    }
}
