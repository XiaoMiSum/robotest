package io.github.xiaomisum.robotest.service.domain.bug;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BugSuggestStreamBridgeTest {

    private final BugSuggestStreamBridge bridge = new BugSuggestStreamBridge();

    @Test
    void attach_registersChannel() {
        SseEmitter emitter = new SseEmitter(1_000L);

        BugSuggestStreamBridge.StreamChannel channel = bridge.attach(emitter);

        assertNotNull(channel.getKey());
        assertFalse(channel.isClosed());
        assertEquals(1, bridge.activeChannels());
    }

    @Test
    void channel_writeAndPingAndClose_doNotThrow() {
        BugSuggestStreamBridge.StreamChannel channel = bridge.attach(new SseEmitter(1_000L));

        // 未初始化 handler 的 emitter 走 early-send 缓冲；关闭异常已被通道吞掉
        channel.send("done", Map.of("ok", true));
        channel.ping();
        channel.close();

        assertTrue(channel.isClosed());
    }

    @Test
    void closedChannel_skipsFurtherWrites() {
        BugSuggestStreamBridge.StreamChannel channel = bridge.attach(new SseEmitter(1_000L));
        channel.close();

        channel.send("error", Map.of("code", 1000018117, "msg", "模型服务调用失败"));
        channel.ping();

        assertTrue(channel.isClosed());
    }

    @Test
    void heartbeat_pingsActiveChannels() {
        bridge.attach(new SseEmitter(1_000L));

        bridge.heartbeat();

        assertEquals(1, bridge.activeChannels());
    }
}
