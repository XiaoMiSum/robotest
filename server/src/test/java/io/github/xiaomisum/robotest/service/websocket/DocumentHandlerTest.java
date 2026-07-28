package io.github.xiaomisum.robotest.service.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import xyz.migoo.framework.websocket.core.WebSocketSessionManager;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentHandlerTest {

    private static final String DOC_ID = "019fa33f-5a77-7d19-87f3-5e05f53ea6ae";
    private static final String SESSION_ID = "session-1";
    private static final String USER_ID = "user-1";

    @Mock
    private WebSocketSessionManager sessionManager;
    @Mock
    private DocumentPersistenceHandler persistenceHandler;
    @Mock
    private WebSocketSession session;

    private DocumentHandler handler;
    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        handler = new DocumentHandler(sessionManager, persistenceHandler);
        attributes = new HashMap<>();
        lenient().when(session.getAttributes()).thenReturn(attributes);
        lenient().when(session.getId()).thenReturn(SESSION_ID);
    }

    @Test
    void handleBinaryMessage_shouldForwardToRoomAndReplyHeartbeat() throws Exception {
        attributes.put("docId", DOC_ID);
        when(sessionManager.getUserId(SESSION_ID)).thenReturn(USER_ID);
        when(session.isOpen()).thenReturn(true);

        byte[] payload = {0x00, 0x01, 0x02};
        handler.handleBinaryMessage(session, new BinaryMessage(payload));

        verify(sessionManager).sendBinaryToRoomExcept(DOC_ID, USER_ID, payload);

        // 单人房间无人转发消息，必须向发送者回空 awareness 帧，
        // 否则 y-websocket 客户端 30 秒收不到消息会判定假死断连重连
        var captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session).sendMessage(captor.capture());
        BinaryMessage heartbeat = (BinaryMessage) captor.getValue();
        assertArrayEquals(new byte[]{0x01, 0x01, 0x00}, heartbeat.getPayload().array());
    }

    @Test
    void handleBinaryMessage_withoutDocId_shouldDoNothing() throws Exception {
        handler.handleBinaryMessage(session, new BinaryMessage(new byte[]{0x00}));

        verify(sessionManager, never()).sendBinaryToRoomExcept(anyString(), anyString(), any());
        verify(session, never()).sendMessage(any());
    }
}
