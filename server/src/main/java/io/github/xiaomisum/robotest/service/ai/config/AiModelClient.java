package io.github.xiaomisum.robotest.service.ai.config;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiUsageLog;
import io.github.xiaomisum.robotest.repository.ai.AiUsageLogMapper;
import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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

    /** 单次生成请求超时：兜底长挂起，整体仍受任务超时清扫（4.2）约束；连接超时沿用底层 OkHttp 默认 10s */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);
    private static final int ERROR_MSG_MAX_LENGTH = 500;

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
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED);
        }
        long start = System.currentTimeMillis();
        try {
            ChatModel model = buildChatModel(request, apiKey);
            AiChatReply reply = extractReply(model.call(buildPrompt(request)));
            writeUsage(request, System.currentTimeMillis() - start, true, reply.tokensIn(), reply.tokensOut(),
                    null);
            return reply;
        } catch (Exception e) {
            int errorCode = ErrorCodeConstants.AI_MODEL_CALL_FAILED.code();
            writeUsage(request, System.currentTimeMillis() - start, false, 0, 0, errorCode);
            throw toServiceException(e, errorCode);
        }
    }

    /** 按行动态配置程序式构建（配置在 DB，不走 starter 自动装配）；maxRetries 随当前设置注入 */
    ChatModel buildChatModel(AiChatRequest request, String apiKey) {
        return OpenAiChatModel.builder()
                .options(buildOptions(request.model(), apiKey, settingsReader.settings().taskMaxRetries()))
                .build();
    }

    /** 程序式构建调用选项：baseUrl / 密钥 / 模型名逐行取值，重试次数承接 task_max_retries（4.2） */
    static OpenAiChatOptions buildOptions(AiModelConfig model, String apiKey, int maxRetries) {
        return OpenAiChatOptions.builder()
                .baseUrl(model.getBaseUrl())
                .apiKey(apiKey)
                .model(model.getModelName())
                .maxRetries(maxRetries)
                .timeout(REQUEST_TIMEOUT)
                .build();
    }

    private static Prompt buildPrompt(AiChatRequest request) {
        List<Message> messages = new ArrayList<>();
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            messages.add(new SystemMessage(request.systemPrompt()));
        }
        messages.add(new UserMessage(request.userPrompt()));
        return new Prompt(messages);
    }

    private static AiChatReply extractReply(ChatResponse response) {
        try {
            String content = String.valueOf(response.getResult().getOutput().getText());
            Usage usage = response.getMetadata().getUsage();
            int tokensIn = usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
            int tokensOut = usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
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

    /** 每次逻辑调用记一条用量明细；记录失败只告警，不中断模型调用（4.3） */
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
}
