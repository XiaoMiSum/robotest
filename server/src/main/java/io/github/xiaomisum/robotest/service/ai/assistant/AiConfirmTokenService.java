package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.service.ai.gateway.AiConfigService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.util.JsonUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 写操作确认令牌管理（详细设计 2.2 / 3.3）。
 *
 * <p>Redis 键 {@code ai:confirm:{token}}，TTL 取系统配置 {@code assistantConfirmTimeoutSeconds}；
 * 一次性消费（getAndDelete），令牌不存在/超时/已消费返回 6011。</p>
 */
@Slf4j
@Component
public class AiConfirmTokenService {

    private static final String KEY_PREFIX = "ai:confirm:";
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;

    @Resource
    private StringRedisTemplate redisTemplate;
    @Resource
    private AiConfigService aiConfigService;

    /**
     * 发放确认令牌
     *
     * @param userId              当前用户
     * @param workspaceId         当前工作空间
     * @param conversationId      所属会话
     * @param assistantMessageId  assistant 消息 ID（tool 消息回填依据）
     * @param toolCallId          工具调用 ID（tool 消息 tool_call_id）
     * @param toolName            工具名
     * @param arguments           LLM 生成的参数
     * @return 令牌 UUID 字符串
     */
    public String issue(UUID userId, UUID workspaceId, UUID conversationId,
                        UUID assistantMessageId, String toolCallId,
                        String toolName, Map<String, Object> arguments) {
        String token = UUID.randomUUID().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId.toString());
        payload.put("workspaceId", workspaceId.toString());
        payload.put("conversationId", conversationId.toString());
        payload.put("assistantMessageId", assistantMessageId.toString());
        payload.put("toolCallId", toolCallId);
        payload.put("toolName", toolName);
        payload.put("arguments", arguments);
        payload.put("createdAt", Instant.now().toEpochMilli());

        int timeoutSeconds = aiConfigService.getIntSetting("assistantConfirmTimeoutSeconds");
        if (timeoutSeconds <= 0) {
            timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + token,
                JsonUtils.toJsonString(payload), timeoutSeconds, TimeUnit.SECONDS);
        log.debug("[AI] 确认令牌已发放 token={} tool={} conv={}", token, toolName, conversationId);
        return token;
    }

    /**
     * 一次性消费令牌（approve / cancel 共用）。
     * 令牌不存在、已消费或归属不匹配时返回 null。
     */
    public ConfirmPayload consume(String token, UUID userId, UUID workspaceId) {
        String json = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + token);
        if (json == null) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = JsonUtils.parseObject(json, Map.class);
            UUID tokenUserId = UUID.fromString((String) map.get("userId"));
            UUID tokenWorkspaceId = UUID.fromString((String) map.get("workspaceId"));
            if (!userId.equals(tokenUserId) || !workspaceId.equals(tokenWorkspaceId)) {
                return null;
            }
            return new ConfirmPayload(
                    tokenUserId,
                    tokenWorkspaceId,
                    UUID.fromString((String) map.get("conversationId")),
                    UUID.fromString((String) map.get("assistantMessageId")),
                    (String) map.get("toolCallId"),
                    (String) map.get("toolName"),
                    (Map<String, Object>) map.get("arguments")
            );
        } catch (Exception e) {
            log.warn("[AI] 令牌解析失败 token={}: {}", token, e.getMessage());
            return null;
        }
    }

    /**
     * 令牌不存在或已消费时抛出 6011
     */
    public ConfirmPayload requireValid(String token, UUID userId, UUID workspaceId) {
        ConfirmPayload payload = consume(token, userId, workspaceId);
        if (payload == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_ASSISTANT_CONFIRM_TOKEN_INVALID);
        }
        return payload;
    }

    /**
     * 确认令牌载荷（从 Redis JSON 反序列化）
     */
    public record ConfirmPayload(
            UUID userId,
            UUID workspaceId,
            UUID conversationId,
            UUID assistantMessageId,
            String toolCallId,
            String toolName,
            Map<String, Object> arguments
    ) {
    }
}
