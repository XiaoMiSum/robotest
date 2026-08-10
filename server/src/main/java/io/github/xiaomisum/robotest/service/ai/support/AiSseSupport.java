package io.github.xiaomisum.robotest.service.ai.support;

import io.github.xiaomisum.robotest.service.ai.provider.OpenAiCompatProvider;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSE 流式响应基础设施：emitter 创建（断开检测/心跳一体化）与帧发送。
 *
 * <p>各 AI 业务服务（网关流式对话、助手对话、approve 回填）共用同一套
 * SSE 生命周期与帧格式，避免各自维护 emitter 样板与心跳线程池。</p>
 *
 * <p><b>超时语义</b>：容器总超时禁用（{@code SSE_TIMEOUT_MILLIS = 0}，Servlet 规范
 * 0 表示永不超时），连接存活完全由心跳证明——每 15s 发送注释帧，send 失败
 * （客户端断开/网络中断）即置 cancelled，上游在下一行边界以取消退出。这样
 * 多轮工具会话（每轮 LLM 调用可能耗时数十秒）不会被总超时强断。</p>
 */
@Slf4j
@Component
public class AiSseSupport {

    /** 容器总超时：0 = 永不超时，存活由心跳检测（见类注释），避免合法长会话被强断 */
    public static final long SSE_TIMEOUT_MILLIS = 0L;
    public static final long PING_INTERVAL_SECONDS = 15L;

    /** SSE 心跳调度（轻量注释行发送，共享单线程即可） */
    private final ScheduledExecutorService pingScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ai-sse-ping");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 单个 SSE 会话句柄：emitter + 共享取消标志 + 心跳句柄。
     *
     * @param emitter  已启动心跳的 emitter
     * @param cancelled 客户端断开/心跳失败即置 true，上游读取在下一行边界以取消退出
     * @param ping     心跳句柄
     */
    public record Channel(SseEmitter emitter, AtomicBoolean cancelled, ScheduledFuture<?> ping) {

        /** 停止心跳（正常结束路径调用；重复调用安全） */
        public void stopPing() {
            ping.cancel(false);
        }
    }

    /**
     * 创建 SSE emitter 并启动心跳：客户端断开（心跳 send 失败）即置取消标志。
     */
    public Channel open() {
        return open(new SseEmitter(SSE_TIMEOUT_MILLIS));
    }

    /**
     * 包可见重载：注册生命周期回调（onTimeout/onCompletion/onError 均置取消标志，
     * onTimeout 额外 complete）并启动心跳。测试可注入 mock emitter 验证回调行为。
     */
    Channel open(SseEmitter emitter) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onError(e -> cancelled.set(true));
        emitter.onTimeout(() -> {
            cancelled.set(true);
            emitter.complete();
        });

        ScheduledFuture<?> ping = pingScheduler.scheduleAtFixedRate(
                () -> ping(emitter, cancelled), PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
        return new Channel(emitter, cancelled, ping);
    }

    /** 单次心跳发送：send 失败（客户端断开/网络中断）即视为会话结束 */
    void ping(SseEmitter emitter, AtomicBoolean cancelled) {
        try {
            emitter.send(SseEmitter.event().comment("ping"));
        } catch (Exception e) {
            cancelled.set(true);
        }
    }

    /** 发送命名事件帧（JSON 数据）；发送失败视为连接断开 */
    public void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            throw new OpenAiCompatProvider.StreamCancelledException();
        }
    }

    /** 发送 error 帧；客户端已断开时静默忽略 */
    public void sendError(SseEmitter emitter, Object code, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(
                    Map.of("code", code, "message", message), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            // 客户端已断开，忽略
        }
    }

    @PreDestroy
    public void shutdown() {
        pingScheduler.shutdownNow();
    }
}
