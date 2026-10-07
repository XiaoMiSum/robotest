package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantMessage;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantMessageMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import xyz.migoo.framework.common.util.JsonUtils;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 助手 SSE 流桥（详设 3.5）：按 assistant messageId 注册事件通道，转发 delta / clarify /
 * preview / citations / done / error 事件并按 15s 间隔发送心跳注释行。
 *
 * <p>断连（客户端关闭 / 超时 / 异常）只把消息条件更新为 interrupted，不通知任务中止——
 * 解析任务继续执行并完整落盘，客户端经 3.6 消息历史轮询补齐至 done（详设 3.5 断线恢复）。</p>
 */
@Component
public class AssistantStreamBridge {

    private static final Logger log = LoggerFactory.getLogger(AssistantStreamBridge.class);

    private static final long PING_PERIOD_MILLIS = 15_000L;

    @Resource
    private AiAssistantMessageMapper messageMapper;

    private final Map<UUID, StreamChannel> channels = new ConcurrentHashMap<>();

    /** 注册事件通道并绑定断连回调；同一 messageId 重复注册以新通道为准 */
    public StreamChannel attach(UUID messageId, SseEmitter emitter) {
        StreamChannel channel = new StreamChannel(messageId, emitter);
        channels.put(messageId, channel);
        emitter.onCompletion(() -> detach(messageId));
        emitter.onTimeout(() -> detach(messageId));
        emitter.onError(error -> detach(messageId));
        return channel;
    }

    /** 取当前通道；客户端已断连或未注册返回 null（调用方降级为只落盘） */
    public StreamChannel get(UUID messageId) {
        return channels.get(messageId);
    }

    /** 断连处理：移出注册表并条件更新 interrupted（仅本人 assistant 消息的 streaming 态生效） */
    public void detach(UUID messageId) {
        channels.remove(messageId);
        messageMapper.update(null, new LambdaUpdateWrapperX<AiAssistantMessage>()
                .eq(AiAssistantMessage::getId, messageId)
                .eq(AiAssistantMessage::getStatus, Constants.AiAssistantMessageStatus.STREAMING)
                .eq(AiAssistantMessage::getRole, Constants.AiAssistantMessageRole.ASSISTANT)
                .set(AiAssistantMessage::getStatus, Constants.AiAssistantMessageStatus.INTERRUPTED));
    }

    /** 心跳（详设 3.5：每 15s 发 `: ping` 注释行，防中间层空闲断连） */
    @Scheduled(fixedRate = PING_PERIOD_MILLIS)
    public void heartbeat() {
        for (StreamChannel channel : channels.values()) {
            channel.ping();
        }
    }

    int activeChannels() {
        return channels.size();
    }

    /** 单条消息的事件通道：写失败即视为客户端断开，只关闭本通道不中断任务 */
    public class StreamChannel {

        private final UUID messageId;
        private final SseEmitter emitter;
        private volatile boolean closed;

        private StreamChannel(UUID messageId, SseEmitter emitter) {
            this.messageId = messageId;
            this.emitter = emitter;
        }

        public UUID getMessageId() {
            return messageId;
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
                log.debug("[AI] 助手 SSE 发送失败转轮询补齐 messageId={}", messageId, e);
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

        /** 终止事件流；断连清理经 onCompletion 回调统一走 detach */
        public void close() {
            closed = true;
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("[AI] 助手 SSE 关闭异常 messageId={}", messageId, e);
            }
        }
    }
}
