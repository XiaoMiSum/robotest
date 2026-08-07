package io.github.xiaomisum.robotest.service.ai.gateway;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AI 调用限流 —— Redis ZSET 滑动窗口（窗口 1 小时），三步 Lua 原子执行。
 *
 * <p>限流检查发生在 LLM 调用前，通过即计数；Redis 不可用时失败开放（放行并记 WARN）。</p>
 */
@Slf4j
@Component
public class AiRateLimiter {

    private static final long WINDOW_MILLIS = 3_600_000L;

    /**
     * KEYS[1] 窗口键；ARGV[1] 窗口起点、ARGV[2] 阈值、ARGV[3] 当前时间戳、ARGV[4] 成员值。
     * 返回 1 放行（已计数）、0 拒绝。
     */
    private static final String LUA_SCRIPT = """
            redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1])
            if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[2]) then
                return 0
            end
            redis.call('ZADD', KEYS[1], ARGV[3], ARGV[4])
            redis.call('EXPIRE', KEYS[1], 3600)
            return 1
            """;

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final AiConfigService aiConfigService;

    public AiRateLimiter(StringRedisTemplate redisTemplate, AiConfigService aiConfigService) {
        this.redisTemplate = redisTemplate;
        this.aiConfigService = aiConfigService;
    }

    /**
     * 检查并计数；超限抛 6004。embedding_index 等无限流类别的功能直接放行。
     */
    public void checkAndRecord(UUID userId, AiFunctionType functionType) {
        AiFunctionType.RateLimitCategory category = functionType.getRateLimitCategory();
        if (category == null) {
            return;
        }
        int limit = aiConfigService.getIntSetting(category.getSettingsKey());
        long now = System.currentTimeMillis();
        String key = "ai:rl:" + userId + ":" + category.name().toLowerCase();
        String member = now + ":" + ThreadLocalRandom.current().nextInt(1_000_000);
        Long allowed;
        try {
            allowed = redisTemplate.execute(SCRIPT, List.of(key),
                    String.valueOf(now - WINDOW_MILLIS), String.valueOf(limit), String.valueOf(now), member);
        } catch (Exception e) {
            // Redis 不可用时失败开放，不阻断 AI 功能
            log.warn("[AI] 限流检查失败开放: {}", e.getMessage());
            return;
        }
        if (allowed == null || allowed == 0) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_RATE_LIMITED);
        }
    }
}
