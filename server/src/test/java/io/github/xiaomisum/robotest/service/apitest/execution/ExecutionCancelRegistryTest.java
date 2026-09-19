package io.github.xiaomisum.robotest.service.apitest.execution;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 取消注册表单测（接口测试域重构方案 04 §3.1.3：幂等与清理独立测试） */
class ExecutionCancelRegistryTest {

    private final ExecutionCancelRegistry registry = new ExecutionCancelRegistry();

    @Test
    void registerAttachesFreshFlagAndRepeatIsIdempotent() {
        UUID id = UUID.randomUUID();
        AtomicBoolean first = registry.register(id);
        assertNotNull(first);
        assertFalse(first.get());

        AtomicBoolean second = registry.register(id);
        assertSame(first, second, "同一执行重复注册必须复用既有标志");
    }

    @Test
    void requestCancellationSetsFlagAndReflectsInIsCancelled() {
        UUID id = UUID.randomUUID();
        registry.register(id);

        assertTrue(registry.requestCancellation(id));
        assertTrue(registry.isCancelled(id));
    }

    @Test
    void requestCancellationOnUnregisteredExecutionReturnsFalse() {
        UUID id = UUID.randomUUID();
        assertFalse(registry.requestCancellation(id), "队列积压尚未挂载标志时返回 false，由编排层直接落 cancelled");
        assertFalse(registry.isCancelled(id));
    }

    @Test
    void releaseClearsFlagAndRepeatReleaseIsIdempotent() {
        UUID id = UUID.randomUUID();
        registry.register(id);
        registry.requestCancellation(id);

        registry.release(id);
        assertFalse(registry.requestCancellation(id), "释放后标志不可再命中");
        registry.release(id);
        assertFalse(registry.isCancelled(id));
    }

    @Test
    void isCancelledOnUnregisteredExecutionReturnsFalse() {
        assertFalse(registry.isCancelled(UUID.randomUUID()));
    }

    @Test
    void flagIsSharedAcrossRegisterAndCancellation() {
        UUID id = UUID.randomUUID();
        AtomicBoolean flag = registry.register(id);
        registry.requestCancellation(id);
        assertEquals(true, flag.get(), "取消必须经由同一标志实例置位");
    }
}