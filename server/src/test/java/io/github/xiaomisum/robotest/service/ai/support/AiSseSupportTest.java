package io.github.xiaomisum.robotest.service.ai.support;


import io.github.xiaomisum.robotest.service.ai.support.AiSseSupport.Channel;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * AiSseSupport 单测：容器总超时禁用（0），连接存活由心跳证明；
 * 生命周期回调（onTimeout/onCompletion/onError）与心跳失败均置 cancelled，
 * 心跳成功保持连接、stopPing 幂等。
 */
class AiSseSupportTest {

    private final AiSseSupport support = new AiSseSupport();

    @AfterEach
    void tearDown() {
        // 关闭心跳调度线程池，避免测试间泄漏调度任务
        support.shutdown();
    }

    @Test
    void sseTimeout_disabled_livenessOwnedByHeartbeat() {
        // 容器总超时为 0（永不超时）：多轮工具会话（每轮 LLM 数十秒）不被总超时强断，
        // 存活检测完全交给心跳——语义上"空闲超时"由 ping 失败承担
        assertEquals(0L, AiSseSupport.SSE_TIMEOUT_MILLIS,
                "容器总超时应为 0，存活由心跳证明");
    }

    @Test
    void open_registersAllLifecycleCallbacks() {
        SseEmitter emitter = mock(SseEmitter.class);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        Channel channel = support.open(emitter);
        try {
            verify(emitter).onCompletion(any(Runnable.class));
            verify(emitter).onError(any());
            verify(emitter).onTimeout(any(Runnable.class));
            assertFalse(channel.cancelled().get());
        } finally {
            channel.stopPing();
        }
    }

    @Test
    void open_onTimeoutCallback_marksCancelledAndCompletesEmitter() {
        SseEmitter emitter = mock(SseEmitter.class);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        Channel channel = support.open(emitter);
        try {
            // 触发容器超时回调（Servlet 容器驱动）：置取消标志并 complete
            Runnable onTimeout = captureRunnableCallback(emitter, (e, c) -> verify(e).onTimeout(c.capture()));
            onTimeout.run();
            assertTrue(channel.cancelled().get());
            verify(emitter).complete();
        } finally {
            channel.stopPing();
        }
    }

    @Test
    void open_onCompletionCallback_marksCancelled() {
        SseEmitter emitter = mock(SseEmitter.class);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        Channel channel = support.open(emitter);
        try {
            // 正常 complete（上游结束）后置取消标志
            Runnable onCompletion = captureRunnableCallback(emitter, (e, c) -> verify(e).onCompletion(c.capture()));
            onCompletion.run();
            assertTrue(channel.cancelled().get());
            // complete 本身由上游调用，不应在 onCompletion 内重复触发
            verify(emitter, never()).complete();
        } finally {
            channel.stopPing();
        }
    }

    @Test
    void open_onErrorCallback_marksCancelled() {
        SseEmitter emitter = mock(SseEmitter.class);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        Channel channel = support.open(emitter);
        try {
            Consumer<Throwable> onError = captureErrorCallback(emitter);
            onError.accept(new IOException("connection reset"));
            assertTrue(channel.cancelled().get());
        } finally {
            channel.stopPing();
        }
    }

    @Test
    void ping_sendFailure_marksCancelled() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        doThrow(new IOException("client gone")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        support.ping(emitter, cancelled);

        assertTrue(cancelled.get());
    }

    @Test
    void ping_sendSuccess_keepsConnectionOpen() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        AtomicBoolean cancelled = new AtomicBoolean(false);

        support.ping(emitter, cancelled);

        assertFalse(cancelled.get());
    }

    @Test
    void stopPing_isIdempotent() {
        SseEmitter emitter = mock(SseEmitter.class);
        Channel channel = support.open(emitter);

        channel.stopPing();
        channel.stopPing();
    }

    /** 捕获注册到 emitter 的 Runnable 回调（onCompletion/onTimeout） */
    private Runnable captureRunnableCallback(SseEmitter emitter, SseCallbackRegistrar registrar) {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        registrar.register(emitter, captor);
        return captor.getValue();
    }

    /** 捕获注册到 emitter 的 onError 回调 */
    @SuppressWarnings("unchecked")
    private Consumer<Throwable> captureErrorCallback(SseEmitter emitter) {
        ArgumentCaptor<Consumer<Throwable>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(emitter).onError(captor.capture());
        return captor.getValue();
    }

    @FunctionalInterface
    private interface SseCallbackRegistrar {
        void register(SseEmitter emitter, ArgumentCaptor<Runnable> captor);
    }
}
