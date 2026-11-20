package io.github.xiaomisum.robotest.service.websocket;

import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private static final UUID PROJECT_ID = UUID.fromString("019fa33f-4444-0000-0000-000000000001");
    private static final UUID REQUIREMENT_ID = UUID.fromString("019fa33f-3333-0000-0000-000000000001");

    private static final String UPDATE_LAYOUT_MSG =
            "{\"type\":\"update_layout\",\"payload\":{\"template\":\"default\"}}";
    private static final String ADD_NODE_MSG =
            "{\"type\":\"add_node\",\"payload\":{\"data\":{\"id\":\"019fa33f-1111-0000-0000-000000000001\","
                    + "\"parentId\":null,\"type\":\"normal\",\"title\":\"new-node\",\"priority\":null,"
                    + "\"sortOrder\":0,\"aiGenerated\":false}}}";
    private static final String ADD_NODE_WITH_REQUIREMENT_MSG =
            "{\"type\":\"add_node\",\"payload\":{\"data\":{\"id\":\"019fa33f-1111-0000-0000-000000000001\","
                    + "\"parentId\":null,\"type\":\"case\",\"title\":\"new-case\",\"priority\":null,"
                    + "\"sortOrder\":0,\"aiGenerated\":true,\"requirementId\":\""
                    + REQUIREMENT_ID + "\"}}}";
    private static final UUID UPDATE_NODE_ID = UUID.fromString("019fa33f-2222-0000-0000-000000000001");
    private static final String UPDATE_ATTRS_MSG =
            "{\"type\":\"update_attrs\",\"payload\":{\"data\":{\"id\":\"019fa33f-2222-0000-0000-000000000001\","
                    + "\"title\":\"登录用例\",\"type\":\"case\",\"priority\":\"P0\",\"sortOrder\":0,"
                    + "\"tags\":[\"登录\",\"限制\"],\"aiGenerated\":true}}}";
    private static final String UPDATE_ATTRS_WITH_REQUIREMENT_MSG =
            "{\"type\":\"update_attrs\",\"payload\":{\"data\":{\"id\":\"019fa33f-2222-0000-0000-000000000001\","
                    + "\"title\":\"登录用例\",\"type\":\"case\",\"priority\":\"P0\","
                    + "\"requirementId\":\"" + REQUIREMENT_ID + "\"}}}";
    private static final String UPDATE_ATTRS_CLEAR_REQUIREMENT_MSG =
            "{\"type\":\"update_attrs\",\"payload\":{\"data\":{\"id\":\"019fa33f-2222-0000-0000-000000000001\","
                    + "\"title\":\"登录用例\",\"type\":\"case\",\"priority\":\"P0\",\"requirementId\":null}}}";

    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private VectorIndexService vectorIndexService;
    @Mock
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Mock
    private RequirementMapper requirementMapper;
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

    private void stubDocumentMember(boolean isMember) {
        when(projectAccessGuard.isDocumentMember(DOC_ID, USER_ID.toString())).thenReturn(isMember);
    }

    private void stubExistingNode() {
        TestCaseNode existing = new TestCaseNode();
        existing.setId(UPDATE_NODE_ID);
        existing.setVersion(3);
        lenient().when(testCaseNodeMapper.selectById(UPDATE_NODE_ID)).thenReturn(existing);
    }

    private void stubRequirement(boolean sameProject) {
        TestCaseDocument document = new TestCaseDocument();
        document.setId(DOC_ID);
        document.setProjectId(PROJECT_ID);
        lenient().when(testCaseDocumentMapper.selectById(DOC_ID)).thenReturn(document);
        Requirement requirement = new Requirement();
        requirement.setId(REQUIREMENT_ID);
        requirement.setProjectId(sameProject ? PROJECT_ID : UUID.randomUUID());
        lenient().when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(requirement);
    }

    @Test
    void persist_withEditPermission_persistsLayout() throws Exception {
        attributes.put("USER_ID", USER_ID.toString());
        stubDocumentMember(true);

        handler.persist(DOC_ID, UPDATE_LAYOUT_MSG, session);

        verify(testCaseDocumentMapper).updateLayout(eq(DOC_ID), any());
        verify(session, never()).sendMessage(any());
    }

    @Test
    void persist_withEditPermission_addNode_insertsNode() throws Exception {
        attributes.put("USER_ID", USER_ID.toString());
        stubDocumentMember(true);

        handler.persist(DOC_ID, ADD_NODE_MSG, session);

        verify(testCaseNodeMapper).insert(any(TestCaseNode.class));
        verify(session, never()).sendMessage(any());
    }

    @Test
    void persist_addNodeWithRequirement_writesColumn() throws Exception {
        attributes.put("USER_ID", USER_ID.toString());
        stubDocumentMember(true);
        stubRequirement(true);

        handler.persist(DOC_ID, ADD_NODE_WITH_REQUIREMENT_MSG, session);

        var captor = ArgumentCaptor.forClass(TestCaseNode.class);
        verify(testCaseNodeMapper).insert(captor.capture());
        assertEquals(REQUIREMENT_ID, captor.getValue().getRequirementId());
        assertEquals(true, captor.getValue().getAiGenerated());
        verify(session, never()).sendMessage(any());
    }

    @Test
    void persist_addNodeWithForeignRequirement_sendsErrorAndLeavesUnlinked() throws Exception {
        attributes.put("USER_ID", USER_ID.toString());
        stubDocumentMember(true);
        stubRequirement(false);

        handler.persist(DOC_ID, ADD_NODE_WITH_REQUIREMENT_MSG, session);

        var captor = ArgumentCaptor.forClass(TestCaseNode.class);
        verify(testCaseNodeMapper).insert(captor.capture());
        assertNull(captor.getValue().getRequirementId());
        var message = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session).sendMessage(message.capture());
        assertTrue(((TextMessage) message.getValue()).getPayload().contains("1000018001"));
    }

    @Test
    void persist_updateAttrsWithTags_passesTagsToMapperAndReindexes() throws Exception {
        attributes.put("USER_ID", USER_ID.toString());
        stubDocumentMember(true);
        stubExistingNode();

        handler.persist(DOC_ID, UPDATE_ATTRS_MSG, session);

        verify(testCaseNodeMapper).updateAttrsWithVersion(UPDATE_NODE_ID, 3, "登录用例", "case", "P0", 0,
                true, List.of("登录", "限制"), false, null);
        verify(vectorIndexService).upsertTestCase(DOC_ID, USER_ID);
    }

    @Test
    void persist_updateAttrsWithRequirement_writesColumn() throws Exception {
        attributes.put("USER_ID", USER_ID.toString());
        stubDocumentMember(true);
        stubExistingNode();
        stubRequirement(true);

        handler.persist(DOC_ID, UPDATE_ATTRS_WITH_REQUIREMENT_MSG, session);

        verify(testCaseNodeMapper).updateAttrsWithVersion(UPDATE_NODE_ID, 3, "登录用例", "case", "P0", null,
                null, null, true, REQUIREMENT_ID);
        verify(session, never()).sendMessage(any());
    }

    @Test
    void persist_updateAttrsClearingRequirement_writesNull() throws Exception {
        attributes.put("USER_ID", USER_ID.toString());
        stubDocumentMember(true);
        stubExistingNode();

        handler.persist(DOC_ID, UPDATE_ATTRS_CLEAR_REQUIREMENT_MSG, session);

        verify(testCaseNodeMapper).updateAttrsWithVersion(UPDATE_NODE_ID, 3, "登录用例", "case", "P0", null,
                null, null, true, null);
        verify(testCaseDocumentMapper, never()).selectById(any());
        verify(session, never()).sendMessage(any());
    }

    @Test
    void persist_updateAttrsWithForeignRequirement_sendsErrorAndPreservesLink() throws Exception {
        attributes.put("USER_ID", USER_ID.toString());
        stubDocumentMember(true);
        stubExistingNode();
        stubRequirement(false);

        handler.persist(DOC_ID, UPDATE_ATTRS_WITH_REQUIREMENT_MSG, session);

        verify(testCaseNodeMapper).updateAttrsWithVersion(UPDATE_NODE_ID, 3, "登录用例", "case", "P0", null,
                null, null, false, null);
        var message = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session).sendMessage(message.capture());
        assertTrue(((TextMessage) message.getValue()).getPayload().contains("1000018001"));
    }

    @Test
    void persist_withoutEditPermission_sendsErrorAndSkipsAllWrites() throws Exception {
        attributes.put("USER_ID", USER_ID.toString());
        stubDocumentMember(false);

        handler.persist(DOC_ID, ADD_NODE_MSG, session);

        verify(testCaseNodeMapper, never()).insert(any(TestCaseNode.class));
        verify(testCaseDocumentMapper, never()).updateLayout(any(), any());

        var captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session).sendMessage(captor.capture());
        String payload = ((TextMessage) captor.getValue()).getPayload();
        assertTrue(payload.contains("PERMISSION_DENIED"));
        assertTrue(payload.contains("非文档成员"));
    }

    @Test
    void persist_withoutUserIdInSession_sendsErrorAndSkipsWrites() throws Exception {
        // session attributes lack USER_ID (normally always set at handshake; defensive fallback)；
        // 守卫对 null userId 判无权限
        handler.persist(DOC_ID, UPDATE_LAYOUT_MSG, session);

        verify(testCaseDocumentMapper, never()).updateLayout(any(), any());
        verify(projectAccessGuard).isDocumentMember(DOC_ID, null);

        var captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session).sendMessage(captor.capture());
        assertTrue(((TextMessage) captor.getValue()).getPayload().contains("PERMISSION_DENIED"));
    }
}
