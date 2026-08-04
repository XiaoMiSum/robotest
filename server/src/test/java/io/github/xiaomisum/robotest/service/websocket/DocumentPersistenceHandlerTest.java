package io.github.xiaomisum.robotest.service.websocket;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.admin.SysRole;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocumentLayout;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysRoleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentLayoutMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentPersistenceHandlerTest {

    private static final UUID DOC_ID = UUID.fromString("019fa33f-5a77-7d19-87f3-5e05f53ea6ae");
    private static final UUID USER_ID = UUID.fromString("019fa33f-0000-0000-0000-000000000001");
    private static final UUID PROJECT_ID = UUID.fromString("019fa33f-0000-0000-0000-000000000002");
    private static final UUID WORKSPACE_ID = UUID.fromString("019fa33f-0000-0000-0000-000000000003");
    private static final UUID ROLE_ID = UUID.fromString("019fa33f-0000-0000-0000-000000000004");

    private static final String UPDATE_LAYOUT_MSG =
            "{\"type\":\"update_layout\",\"payload\":{\"template\":\"default\"}}";
    private static final String ADD_NODE_MSG =
            "{\"type\":\"add_node\",\"payload\":{\"data\":{\"id\":\"019fa33f-1111-0000-0000-000000000001\","
                    + "\"parentId\":null,\"type\":\"normal\",\"title\":\"new-node\",\"priority\":null,"
                    + "\"sortOrder\":0,\"aiGenerated\":false}}}";

    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private TestCaseDocumentLayoutMapper testCaseDocumentLayoutMapper;
    @Mock
    private TestCaseModuleMapper testCaseModuleMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private WorkspaceUserMapper workspaceUserMapper;
    @Mock
    private SysRoleMapper sysRoleMapper;
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

    private void stubDocument() {
        TestCaseModule document = new TestCaseModule();
        document.setId(DOC_ID);
        document.setProjectId(PROJECT_ID);
        document.setType(Constants.ModuleType.DOCUMENT);
        when(testCaseModuleMapper.selectById(DOC_ID)).thenReturn(document);

        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setWorkspaceId(WORKSPACE_ID);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
    }

    private void stubWorkspaceRole(boolean hasEditPermission) {
        WorkspaceUser workspaceUser = new WorkspaceUser();
        workspaceUser.setUserId(USER_ID);
        workspaceUser.setWorkspaceId(WORKSPACE_ID);
        workspaceUser.setWorkspaceRole(ROLE_ID);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID)).thenReturn(workspaceUser);

        SysRole role = new SysRole();
        role.setId(ROLE_ID);
        role.setPermissions(hasEditPermission ? List.of("case:view", "case:edit") : List.of("case:view"));
        when(sysRoleMapper.selectById(ROLE_ID)).thenReturn(role);
    }

    @Test
    void persist_withEditPermission_persistsLayout() throws Exception {
        attributes.put("USER_ID", USER_ID.toString());
        stubDocument();
        stubWorkspaceRole(true);

        handler.persist(DOC_ID, UPDATE_LAYOUT_MSG, session);

        verify(testCaseDocumentLayoutMapper).insert(any(TestCaseDocumentLayout.class));
        verify(session, never()).sendMessage(any());
    }

    @Test
    void persist_withEditPermission_addNode_insertsNode() throws Exception {
        attributes.put("USER_ID", USER_ID.toString());
        stubDocument();
        stubWorkspaceRole(true);

        handler.persist(DOC_ID, ADD_NODE_MSG, session);

        verify(testCaseNodeMapper).insert(any(TestCaseNode.class));
        verify(session, never()).sendMessage(any());
    }

    @Test
    void persist_withoutEditPermission_sendsErrorAndSkipsAllWrites() throws Exception {
        attributes.put("USER_ID", USER_ID.toString());
        stubDocument();
        stubWorkspaceRole(false);

        handler.persist(DOC_ID, ADD_NODE_MSG, session);

        verify(testCaseNodeMapper, never()).insert(any(TestCaseNode.class));
        verify(testCaseDocumentLayoutMapper, never()).insert(any(TestCaseDocumentLayout.class));
        verify(testCaseDocumentLayoutMapper, never()).updateById(any(TestCaseDocumentLayout.class));

        var captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session).sendMessage(captor.capture());
        String payload = ((TextMessage) captor.getValue()).getPayload();
        assertTrue(payload.contains("PERMISSION_DENIED"));
        assertTrue(payload.contains("无文档编辑权限"));
    }

    @Test
    void persist_withoutUserIdInSession_sendsErrorAndSkipsWrites() throws Exception {
        // session attributes lack USER_ID (normally always set at handshake; defensive fallback)

        handler.persist(DOC_ID, UPDATE_LAYOUT_MSG, session);

        verify(testCaseDocumentLayoutMapper, never()).insert(any(TestCaseDocumentLayout.class));
        verify(testCaseDocumentLayoutMapper, never()).updateById(any(TestCaseDocumentLayout.class));

        var captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session).sendMessage(captor.capture());
        assertTrue(((TextMessage) captor.getValue()).getPayload().contains("PERMISSION_DENIED"));
    }

    @Test
    void persist_userNotInWorkspace_sendsErrorAndSkipsWrites() throws Exception {
        attributes.put("USER_ID", USER_ID.toString());
        stubDocument();
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID)).thenReturn(null);

        handler.persist(DOC_ID, UPDATE_LAYOUT_MSG, session);

        verify(testCaseDocumentLayoutMapper, never()).insert(any(TestCaseDocumentLayout.class));
        var captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session).sendMessage(captor.capture());
        assertTrue(((TextMessage) captor.getValue()).getPayload().contains("PERMISSION_DENIED"));
    }
}
