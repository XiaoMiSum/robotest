package io.github.xiaomisum.robotest.service.ai.config;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import io.github.xiaomisum.robotest.model.entity.ai.AiEmbeddingConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiUsageLog;
import io.github.xiaomisum.robotest.repository.ai.AiUsageLogMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 向量 API 客户端（OpenAI 兼容 /embeddings）：供连通性测试（3.4）与向量重建（4.4）复用。
 * 传输、序列化、重试与响应解析统一由 Spring AI 2.0.x 执行，平台保留门面：密钥解密、
 * 数量 / 维度严格校验与逐次记账；maxRetries = 0 沿用原单次调用语义（不自动重试）。
 * 每次逻辑调用（成功与失败）记一条 ai_usage_log（4.3）：call_type = embedding、model_id = NULL、cost = 0。
 */
@Component
public class AiEmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(AiEmbeddingClient.class);

    /** 单次向量化请求超时；连接超时沿用底层 OkHttp 默认 10s */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final int ERROR_MSG_MAX_LENGTH = 500;

    private final byte[] secretKey;

    @Resource
    private AiUsageLogMapper usageLogMapper;

    public AiEmbeddingClient(@Value("${robotest.env.secret-key:}") String base64SecretKey) {
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
     * 批量文本向量化（详设 4.4 分块重嵌复用同一次调用）：严格校验响应数量与维度，
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
        long start = System.currentTimeMillis();
        try {
            OpenAiEmbeddingOptions options = buildOptions(config, apiKey);
            EmbeddingResponse response = buildEmbeddingModel(options)
                    .call(new EmbeddingRequest(inputs, options));
            EmbeddingsReply reply = extractReply(response, strict, inputs.size(), config.getDimensions());
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

    /** 按行动态配置程序式构建（配置在 DB，不走 starter 自动装配）；maxRetries=0 保持原单次调用语义 */
    static OpenAiEmbeddingOptions buildOptions(AiEmbeddingConfig config, String apiKey) {
        return OpenAiEmbeddingOptions.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(apiKey)
                .model(config.getEmbeddingModel())
                .maxRetries(0)
                .timeout(REQUEST_TIMEOUT)
                .build();
    }

    EmbeddingModel buildEmbeddingModel(OpenAiEmbeddingOptions options) {
        return OpenAiEmbeddingModel.builder().options(options).build();
    }

    private static EmbeddingsReply extractReply(EmbeddingResponse response, boolean strict, int expectedCount,
            Integer expectedDimensions) {
        try {
            List<Embedding> results = response.getResults();
            if (results == null || results.isEmpty()) {
                throw new IllegalArgumentException("向量响应缺少 data");
            }
            List<List<Double>> vectors = new ArrayList<>(results.size());
            for (Embedding item : results) {
                float[] output = item.getOutput();
                List<Double> vector = new ArrayList<>(output.length);
                for (float value : output) {
                    vector.add((double) value);
                }
                vectors.add(vector);
            }
            Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
            int tokens = usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
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

    /** 向量化回复：dimensions 用于维度一致性校验（3.4 测试响应与 4.4 重建） */
    public record EmbeddingReply(List<Double> embedding, int dimensions, int tokens) {
    }

    /** 批量向量化回复：vectors 与输入一一对应（严格模式已校验数量与维度） */
    public record EmbeddingsReply(List<List<Double>> vectors, int tokens) {
    }
}
