package io.github.xiaomisum.robotest.service.domain.bug;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import xyz.migoo.framework.common.util.JsonUtils;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 建议 SSE 流桥（详设 3.5）：按请求级随机 key 注册事件通道并每 15s 发心跳注释行。
 *
 * <p>不复用 {@code AssistantStreamBridge}：助手按持久化 messageId 注册（任务线程只能持有 ID），
 * 断连回调写库标记 interrupted；建议不产生任务、不落库，异步闭包直持 emitter，
 * 断连即终态——只移出注册表，无状态可补。</p>
 */
@Component
public class BugSuggestStreamBridge {

    private static final Logger log = LoggerFactory.getLogger(BugSuggestStreamBridge.class);

    private static final long PING_PERIOD_MILLIS = 15_000L;

    private final Map<UUID, StreamChannel> channels = new ConcurrentHashMap<>();

    /** 注册事件通道并绑定断连回调；断连 / 完成 / 超时均只移出注册表 */
    public StreamChannel attach(SseEmitter emitter) {
        UUID key = UUID.randomUUID();
        channels.put(key, new StreamChannel(key, emitter));
        emitter.onCompletion(() -> channels.remove(key));
        emitter.onTimeout(() -> channels.remove(key));
        emitter.onError(error -> channels.remove(key));
        return channels.get(key);
    }

    /** 心跳（详设 3.5：每 15s 发 `: ping` 注释行，防中间层空闲断连） */
    @Scheduled(fixedRate = PING_PERIOD_MILLIS)
    public void heartbeat() {
        channels.values().forEach(StreamChannel::ping);
    }

    int activeChannels() {
        return channels.size();
    }

    /** 单请求事件通道：写失败即视为客户端断开，只关闭本通道，不再写后续事件 */
    public static class StreamChannel {

        private final UUID key;
        private final SseEmitter emitter;
        private volatile boolean closed;

        private StreamChannel(UUID key, SseEmitter emitter) {
            this.key = key;
            this.emitter = emitter;
        }

        public UUID getKey() {
            return key;
        }

        public boolean isClosed() {
            return closed;
        }

        /** 发送事件：data 为已序列化语义的单行 JSON（事件契约见详设 3.5） */
        public void send(String event, Object data) {
            if (closed) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name(event).data(JsonUtils.toJsonString(data)));
            } catch (IOException | IllegalStateException e) {
                log.debug("[AI] 建议 SSE 发送失败视为断开 key={}", key, e);
                closed = true;
            }
        }

        /** 心跳注释行 */
        public void ping() {
            if (closed) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                closed = true;
            }
        }

        /** 终止事件流；断连清理经 onCompletion 回调统一移出注册表 */
        public void close() {
            closed = true;
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("[AI] 建议 SSE 关闭异常 key={}", key, e);
            }
        }
    }
}
