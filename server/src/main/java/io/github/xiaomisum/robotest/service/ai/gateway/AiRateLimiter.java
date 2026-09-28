package io.github.xiaomisum.robotest.service.ai.gateway;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.GlobalErrorCodeConstants;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.web.core.ratelimit.RateLimiter;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

/**
 * AI 调用限流（安全规范 6.1）：委托框架 {@link RateLimiter} 按固定窗口（1 小时）计数，
 * 统计键 ai:{userId}:{category}，超限抛框架全局错误码 429。
 *
 * <p>限流检查发生在 LLM 调用前，通过即计数；计数走 {@code StateStore}，Redis 不可用时
 * 失败开放（放行并记录 WARN），不阻断 AI 功能。</p>
 */
@Slf4j
@Component
public class AiRateLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final RateLimiter rateLimiter;
    private final AiConfigService aiConfigService;

    public AiRateLimiter(RateLimiter rateLimiter, AiConfigService aiConfigService) {
        this.rateLimiter = rateLimiter;
        this.aiConfigService = aiConfigService;
    }

    /**
     * 检查并计数；超限抛框架全局 429。embedding_index 等无限流类别的功能直接放行。
     */
    public void checkAndRecord(UUID userId, AiFunctionType functionType) {
        AiFunctionType.RateLimitCategory category = functionType.getRateLimitCategory();
        if (category == null) {
            return;
        }
        int limit = aiConfigService.getIntSetting(category.getSettingsKey());
        String key = "ai:" + userId + ":" + category.name().toLowerCase(Locale.ROOT);
        boolean allowed;
        try {
            allowed = rateLimiter.tryAcquire(key, limit, WINDOW);
        } catch (Exception e) {
            // 计数链路异常按失败开放处理，不阻断 AI 功能
            log.warn("[AI] 限流检查失败开放: {}", e.getMessage());
            return;
        }
        if (!allowed) {
            throw ServiceExceptionUtil.get(GlobalErrorCodeConstants.TOO_MANY_REQUESTS);
        }
    }
}
