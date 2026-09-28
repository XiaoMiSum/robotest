package io.github.xiaomisum.robotest.framework.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.web.core.store.StateStore;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 失败开放装饰器：底层存储故障时限流/锁定/撤销检查一律放行（安全规范 6.1）。
 */
@ExtendWith(MockitoExtension.class)
class FailOpenStateStoreTest {

    private static final Duration TTL = Duration.ofSeconds(60);

    @Mock
    private StateStore delegate;

    private FailOpenStateStore store;

    @BeforeEach
    void setUp() {
        store = new FailOpenStateStore(delegate);
    }

    private static IllegalStateException redisDown() {
        return new IllegalStateException("redis down");
    }

    @Test
    void delegateWorks_operationsArePassedThrough() {
        when(delegate.increment("k", TTL)).thenReturn(3L);
        when(delegate.get("k")).thenReturn(2L);
        when(delegate.setIfAbsent("k2", TTL)).thenReturn(false);

        assertEquals(3L, store.increment("k", TTL));
        assertEquals(2L, store.get("k"));
        assertFalse(store.setIfAbsent("k2", TTL));
        store.put("k3", 1L, TTL);
        store.delete("k4");
        verify(delegate).put("k3", 1L, TTL);
        verify(delegate).delete("k4");
    }

    @Test
    void delegateFails_incrementReturnsZeroSoRateLimitAllows() {
        when(delegate.increment("k", TTL)).thenThrow(redisDown());
        assertEquals(0L, store.increment("k", TTL));
    }

    @Test
    void delegateFails_getReturnsZeroSoRevocationCheckPasses() {
        when(delegate.get("k")).thenThrow(redisDown());
        assertEquals(0L, store.get("k"));
    }

    @Test
    void delegateFails_setIfAbsentReturnsTrueSoIdempotencyPasses() {
        when(delegate.setIfAbsent("k2", TTL)).thenThrow(redisDown());
        assertTrue(store.setIfAbsent("k2", TTL));
    }

    @Test
    void delegateFails_putAndDeleteAreSwallowed() {
        doThrow(redisDown()).when(delegate).put(anyString(), anyLong(), any());
        doThrow(redisDown()).when(delegate).delete(anyString());

        assertDoesNotThrow(() -> store.put("k3", 1L, TTL));
        assertDoesNotThrow(() -> store.delete("k4"));
        verify(delegate).put("k3", 1L, TTL);
        verify(delegate).delete("k4");
    }
}
