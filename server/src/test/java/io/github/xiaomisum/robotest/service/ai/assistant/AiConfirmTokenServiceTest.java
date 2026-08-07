package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.service.ai.gateway.AiConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiConfirmTokenServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private AiConfigService aiConfigService;

    @InjectMocks
    private AiConfirmTokenService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID workspaceId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID assistantMessageId = UUID.randomUUID();

    @Test
    void issue_storesPayloadWithConfiguredTimeout() {
        when(aiConfigService.getIntSetting("assistantConfirmTimeoutSeconds")).thenReturn(120);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        String token = service.issue(userId, workspaceId, conversationId, assistantMessageId,
                "call_1", "create_bug", Map.of("title", "缺陷"));

        assertNotNull(token);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(keyCaptor.capture(), valueCaptor.capture(), eq(120L), eq(TimeUnit.SECONDS));
        assertTrue(keyCaptor.getValue().startsWith("ai:confirm:"));
        assertTrue(valueCaptor.getValue().contains("create_bug"));
        assertTrue(valueCaptor.getValue().contains(userId.toString()));
        assertTrue(valueCaptor.getValue().contains(workspaceId.toString()));
    }

    @Test
    void issue_fallsBackToDefaultTimeoutWhenConfiguredNonPositive() {
        when(aiConfigService.getIntSetting("assistantConfirmTimeoutSeconds")).thenReturn(0);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        service.issue(userId, workspaceId, conversationId, assistantMessageId,
                "call_1", "create_bug", Map.of());

        verify(valueOps).set(startsWith("ai:confirm:"), anyString(), eq(300L), eq(TimeUnit.SECONDS));
    }

    @Test
    void consume_returnsPayloadWhenOwnerMatches() {
        String token = "token-1";
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.getAndDelete("ai:confirm:" + token)).thenReturn(
                payloadJson(userId, workspaceId, conversationId, assistantMessageId,
                        "call_1", "create_bug", Map.of("title", "缺陷")));

        AiConfirmTokenService.ConfirmPayload payload = service.consume(token, userId, workspaceId);

        assertNotNull(payload);
        assertEquals(userId, payload.userId());
        assertEquals(workspaceId, payload.workspaceId());
        assertEquals(conversationId, payload.conversationId());
        assertEquals(assistantMessageId, payload.assistantMessageId());
        assertEquals("call_1", payload.toolCallId());
        assertEquals("create_bug", payload.toolName());
        assertEquals("缺陷", payload.arguments().get("title"));
        verify(valueOps).getAndDelete("ai:confirm:" + token);
    }

    @Test
    void consume_returnsNullWhenRedisMiss() {
        String token = "missing";
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.getAndDelete("ai:confirm:" + token)).thenReturn(null);

        assertNull(service.consume(token, userId, workspaceId));
        verify(valueOps).getAndDelete("ai:confirm:" + token);
    }

    @Test
    void consume_returnsNullWhenOwnerMismatch() {
        String token = "foreign";
        UUID other = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.getAndDelete("ai:confirm:" + token)).thenReturn(
                payloadJson(other, workspaceId, conversationId, assistantMessageId,
                        "call_1", "create_bug", Map.of()));

        assertNull(service.consume(token, userId, workspaceId));
        verify(valueOps).getAndDelete("ai:confirm:" + token);
    }

    @Test
    void consume_returnsNullOnMalformedJson() {
        String token = "bad";
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.getAndDelete("ai:confirm:" + token)).thenReturn("not-a-json");

        assertNull(service.consume(token, userId, workspaceId));
    }

    @Test
    void requireValid_returnsPayloadWhenTokenValid() {
        String token = "token-2";
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.getAndDelete("ai:confirm:" + token)).thenReturn(
                payloadJson(userId, workspaceId, conversationId, assistantMessageId,
                        "call_2", "create_plan_draft", Map.of()));

        AiConfirmTokenService.ConfirmPayload payload = service.requireValid(token, userId, workspaceId);

        assertNotNull(payload);
        assertEquals("create_plan_draft", payload.toolName());
    }

    @Test
    void requireValid_throwsWhenTokenInvalid() {
        String token = "expired";
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.getAndDelete("ai:confirm:" + token)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.requireValid(token, userId, workspaceId));

        assertEquals(1000013011, ex.getCode());
    }

    private String payloadJson(UUID uid, UUID ws, UUID conv, UUID asstMsg,
                               String callId, String toolName, Map<String, Object> args) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", uid.toString());
        payload.put("workspaceId", ws.toString());
        payload.put("conversationId", conv.toString());
        payload.put("assistantMessageId", asstMsg.toString());
        payload.put("toolCallId", callId);
        payload.put("toolName", toolName);
        payload.put("arguments", args);
        payload.put("createdAt", 12345L);
        return JsonUtils.toJsonString(payload);
    }
}
