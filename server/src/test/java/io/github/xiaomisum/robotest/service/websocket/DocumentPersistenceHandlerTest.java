package io.github.xiaomisum.robotest.service.websocket;

import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.vector.VectorIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentPersistenceHandlerTest {

    private static final UUID DOC_ID = UUID.fromString("019fa33f-5a77-7d19-87f3-5e05f53ea6ae");
    private static final UUID USER_ID = UUID.fromString("019fa33f-0000-0000-0000-000000000001");

    private static final String UPDATE_LAYOUT_MSG =
            "{\"type\":\"update_layout\",\"payload\":{\"template\":\"default\"}}";
    private static final String ADD_NODE_MSG =
            "{\"type\":\"add_node\",\"payload\":{\"data\":{\"id\":\"019fa33f-1111-0000-0000-000000000001\","
                    + "\"parentId\":null,\"type\":\"normal\",\"title\":\"new-node\",\"priority\":null,"
                    + "\"sortOrder\":0,\"aiGenerated\":false}}}";

    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private VectorIndexService vectorIndexService;
    @Mock
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Mock
    private ProjectAccessGuard projectAccessGuard;
    @Mock
    private WebSocketSession session;

    @InjectMocks
    private DocumentPersistenceHandler handler;

    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        attributes = new HashMap<>();
        lenient().when(session.getAttributes()).thenReturn(attributes);
        lenient().when(session.isOpen()).thenReturn(true);
        lenient().when(session.getId()).thenReturn("session-1");
    }

    private void stubEditPermission(boolean granted) {
        when(projectAccessGuard.hasDocumentEditPermission(DOC_ID, USER_ID.toString())).thenReturn(granted);
    }

    @Test
    void persist_withEditPermission_persistsLayout() throws Exception {
        attributes.put("USER_ID", USER_ID.toString());
        stubEditPermission(true);

        handler.persist(DOC_ID, UPDATE_LAYOUT_MSG, session);

        verify(testCaseDocumentMapper).updateLayout(eq(DOC_ID), any());
        verify(session, never()).sendMessage(any());
    }

    @Test
    void persist_withEditPermission_addNode_insertsNode() throws Exception {
        attributes.put("USER_ID", USER_ID.toString());
        stubEditPermission(true);

        handler.persist(DOC_ID, ADD_NODE_MSG, session);

        verify(testCaseNodeMapper).insert(any(TestCaseNode.class));
        verify(session, never()).sendMessage(any());
    }

    @Test
    void persist_withoutEditPermission_sendsErrorAndSkipsAllWrites() throws Exception {
        attributes.put("USER_ID", USER_ID.toString());
        stubEditPermission(false);

        handler.persist(DOC_ID, ADD_NODE_MSG, session);

        verify(testCaseNodeMapper, never()).insert(any(TestCaseNode.class));
        verify(testCaseDocumentMapper, never()).updateLayout(any(), any());

        var captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session).sendMessage(captor.capture());
        String payload = ((TextMessage) captor.getValue()).getPayload();
        assertTrue(payload.contains("PERMISSION_DENIED"));
        assertTrue(payload.contains("无文档编辑权限"));
    }

    @Test
    void persist_withoutUserIdInSession_sendsErrorAndSkipsWrites() throws Exception {
        // session attributes lack USER_ID (normally always set at handshake; defensive fallback)；
        // 守卫对 null userId 判无权限
        handler.persist(DOC_ID, UPDATE_LAYOUT_MSG, session);

        verify(testCaseDocumentMapper, never()).updateLayout(any(), any());
        verify(projectAccessGuard).hasDocumentEditPermission(DOC_ID, null);

        var captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session).sendMessage(captor.capture());
        assertTrue(((TextMessage) captor.getValue()).getPayload().contains("PERMISSION_DENIED"));
    }
}
