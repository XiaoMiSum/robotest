package io.github.xiaomisum.robotest.service.ai.gateway;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.GlobalErrorCodeConstants;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.web.core.ratelimit.RateLimiter;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AI 限流：委托框架 RateLimiter 固定窗口（1 小时）计数，超限返回框架全局 429（安全规范 6.1）。
 */
@ExtendWith(MockitoExtension.class)
class AiRateLimiterTest {

    @Mock
    private RateLimiter rateLimiter;
    @Mock
    private AiConfigService aiConfigService;

    private AiRateLimiter rateLimiterFacade;

    private final UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        rateLimiterFacade = new AiRateLimiter(rateLimiter, aiConfigService);
    }

    @Test
    void categoryUnlimited_passesWithoutTouchingStore() {
        rateLimiterFacade.checkAndRecord(userId, AiFunctionType.EMBEDDING_INDEX);
        verifyNoInteractions(rateLimiter, aiConfigService);
    }

    @Test
    void withinLimit_acquiresWithCategoryKeyAndOneHourWindow() {
        when(aiConfigService.getIntSetting("rateLimit.generation")).thenReturn(20);
        when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(true);

        assertDoesNotThrow(() -> rateLimiterFacade.checkAndRecord(userId, AiFunctionType.CASE_GENERATION));

        verify(rateLimiter).tryAcquire("ai:" + userId + ":generation", 20, Duration.ofHours(1));
    }

    @Test
    void exceedsLimit_throwsFrameworkTooManyRequests() {
        when(aiConfigService.getIntSetting("rateLimit.generation")).thenReturn(20);
        when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(false);

        ServiceException e = assertThrows(ServiceException.class,
                () -> rateLimiterFacade.checkAndRecord(userId, AiFunctionType.CASE_GENERATION));
        assertEquals(GlobalErrorCodeConstants.TOO_MANY_REQUESTS.code(), e.getCode());
    }

    @Test
    void rateLimiterFailure_failsOpen() {
        when(aiConfigService.getIntSetting("rateLimit.generation")).thenReturn(20);
        when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenThrow(new IllegalStateException("redis down"));

        assertDoesNotThrow(() -> rateLimiterFacade.checkAndRecord(userId, AiFunctionType.CASE_GENERATION));
    }
}
