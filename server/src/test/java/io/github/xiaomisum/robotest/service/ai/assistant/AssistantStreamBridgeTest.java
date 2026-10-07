package io.github.xiaomisum.robotest.service.ai.assistant;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantMessage;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantMessageMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 助手 SSE 流桥单测（详设 3.5）：通道注册与断连回收、条件更新 interrupted、心跳与写失败降级（C8）。
 */
@ExtendWith(MockitoExtension.class)
class AssistantStreamBridgeTest {

    private static final UUID MESSAGE_ID = UUID.randomUUID();

    /** 纯单测环境无 MyBatis 初始化：detached 条件更新的 lambda 列解析依赖 TableInfo 缓存 */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        if (TableInfoHelper.getTableInfo(AiAssistantMessage.class) == null) {
            TableInfoHelper.initTableInfo(assistant, AiAssistantMessage.class);
        }
    }

    @Mock
    private AiAssistantMessageMapper messageMapper;

    private AssistantStreamBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new AssistantStreamBridge();
        ReflectionTestUtils.setField(bridge, "messageMapper", messageMapper);
    }

    @Test
    void attach_registersChannel_andGetReturnsSameChannel() {
        SseEmitter emitter = new SseEmitter();

        AssistantStreamBridge.StreamChannel channel = bridge.attach(MESSAGE_ID, emitter);

        assertNotNull(channel);
        assertSame(channel, bridge.get(MESSAGE_ID));
        assertEquals(MESSAGE_ID, channel.getMessageId());
        assertFalse(channel.isClosed());
        assertEquals(1, bridge.activeChannels());
        assertNull(bridge.get(UUID.randomUUID()));
        // 注册本身不触碰数据层（断连回调才落 interrupted）
        verifyNoInteractions(messageMapper);
    }

    @Test
    void detach_removesChannelAndUpdatesStreamingToInterrupted() {
        bridge.attach(MESSAGE_ID, new SseEmitter());

        bridge.detach(MESSAGE_ID);

        assertNull(bridge.get(MESSAGE_ID));
        assertEquals(0, bridge.activeChannels());
        // C11：条件更新不带实体载体，仅按 id + streaming + assistant 定位并置 interrupted
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaUpdateWrapperX<AiAssistantMessage>> captor =
                ArgumentCaptor.forClass((Class) LambdaUpdateWrapperX.class);
        verify(messageMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapperX<AiAssistantMessage> wrapper = captor.getValue();
        // eq 条件参数在 SQL 段惰性求值时才入参，先触发再断言
        wrapper.getSqlSegment();
        assertTrue(wrapper.getParamNameValuePairs().containsValue("streaming"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("assistant"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("interrupted"));
    }

    @Test
    void heartbeat_pingsEveryActiveChannel() throws IOException {
        SseEmitter emitter = spy(new SseEmitter());
        bridge.attach(MESSAGE_ID, emitter);
        bridge.attach(UUID.randomUUID(), new SseEmitter());

        bridge.heartbeat();

        // 心跳注释行经 send(SseEventBuilder) 下发；写失败不影响其余通道
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        assertEquals(2, bridge.activeChannels());
    }

    @Test
    void send_writeFailure_marksChannelClosed_andSkipsFurtherWrites() throws IOException {
        SseEmitter emitter = spy(new SseEmitter());
        doThrow(new IOException("broken pipe")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        AssistantStreamBridge.StreamChannel channel = bridge.attach(MESSAGE_ID, emitter);

        channel.send("delta", Map.of("text", "你好"));

        assertTrue(channel.isClosed());
        // 写失败只关本通道，任务与数据层不受影响
        verifyNoInteractions(messageMapper);

        channel.send("done", Map.of("messageId", MESSAGE_ID));
        channel.ping();
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void close_completesEmitter_andStopsFurtherSends() throws IOException {
        SseEmitter emitter = spy(new SseEmitter());
        AssistantStreamBridge.StreamChannel channel = bridge.attach(MESSAGE_ID, emitter);

        channel.close();

        assertTrue(channel.isClosed());
        channel.send("delta", Map.of("text", "x"));
        channel.ping();
        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        // 二次关闭幂等
        channel.close();
    }

    @Test
    void attach_replacesExistingChannelForSameMessage() {
        AssistantStreamBridge.StreamChannel first = bridge.attach(MESSAGE_ID, new SseEmitter());
        AssistantStreamBridge.StreamChannel second = bridge.attach(MESSAGE_ID, new SseEmitter());

        assertSame(second, bridge.get(MESSAGE_ID));
        assertEquals(1, bridge.activeChannels());
        assertFalse(first.isClosed());
    }

    @Test
    void heartbeat_ignoresFailedChannel() throws IOException {
        SseEmitter failing = spy(new SseEmitter());
        doThrow(new IOException("gone")).when(failing).send(any(SseEmitter.SseEventBuilder.class));
        AssistantStreamBridge.StreamChannel closed = bridge.attach(MESSAGE_ID, failing);
        closed.ping();
        assertTrue(closed.isClosed());

        // 已失败通道不再尝试写入，也不抛出
        bridge.heartbeat();
        verify(failing, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }
}
