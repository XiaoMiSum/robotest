package io.github.xiaomisum.robotest.service.workspace;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.request.workspace.WorkspaceDefaultProjectReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.workspace.WorkspaceUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceContextRespDTO;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.model.entity.workspace.Workspace;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceContextServiceImplTest {

    @Mock
    private WorkspaceMapper workspaceMapper;
    @Mock
    private WorkspaceUserMapper workspaceUserMapper;
    @Mock
    private ProjectMapper projectMapper;

    @InjectMocks
    private WorkspaceContextServiceImpl contextService;

    private final UUID workspaceId = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private final UUID userId = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private final UUID projectId = UUID.fromString("00000000-0000-0000-0000-0000000000cc");
    private final UUID workspaceUserId = UUID.fromString("00000000-0000-0000-0000-0000000000dd");

    private Workspace workspace() {
        Workspace ws = new Workspace();
        ws.setId(workspaceId);
        ws.setName("QA 团队");
        ws.setStatus(Constants.Status.ACTIVE);
        return ws;
    }

    private WorkspaceUser adminUser() {
        WorkspaceUser wu = new WorkspaceUser();
        wu.setId(workspaceUserId);
        wu.setUserId(userId);
        wu.setWorkspaceId(workspaceId);
        wu.setWorkspaceRole(Constants.WorkspaceRole.ADMIN_ID);
        return wu;
    }

    private void stubBase() {
        when(workspaceMapper.selectById(workspaceId)).thenReturn(workspace());
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(adminUser());
    }

    /** 仅成功路径需要成员/项目计数（buildContextRespDTO 内部查询） */
    private void stubCounts() {
        when(workspaceUserMapper.countByWorkspaceId(workspaceId)).thenReturn(3L);
        when(projectMapper.countByWorkspaceId(workspaceId)).thenReturn(2L);
    }

    // ========== getWorkspaceContext ==========

    @Test
    void getWorkspaceContext_success() {
        stubBase();
        stubCounts();

        WorkspaceContextRespDTO result = contextService.getWorkspaceContext(userId, workspaceId);

        assertEquals(workspaceId, result.getId());
        assertEquals("QA 团队", result.getName());
        assertEquals("active", result.getStatus());
        assertEquals(Constants.WorkspaceRole.ADMIN_ID.toString(), result.getWorkspaceRole());
        assertEquals(3L, result.getMemberCount());
        assertEquals(2L, result.getProjectCount());
    }

    @Test
    void getWorkspaceContext_workspaceMissing_throws() {
        when(workspaceMapper.selectById(workspaceId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> contextService.getWorkspaceContext(userId, workspaceId));
    }

    @Test
    void getWorkspaceContext_userNotMember_throws() {
        when(workspaceMapper.selectById(workspaceId)).thenReturn(workspace());
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> contextService.getWorkspaceContext(userId, workspaceId));
    }

    // ========== updateWorkspace ==========

    @Test
    void updateWorkspace_success_partialUpdateCarrier() {
        stubBase();
        stubCounts();

        WorkspaceUpdateReqDTO req = new WorkspaceUpdateReqDTO();
        req.setName("QA 改名");
        req.setDescription("新版描述");
        WorkspaceContextRespDTO result = contextService.updateWorkspace(userId, workspaceId, req);

        assertEquals("QA 改名", result.getName());
        assertEquals("新版描述", result.getDescription());
        ArgumentCaptor<Workspace> captor = ArgumentCaptor.forClass(Workspace.class);
        verify(workspaceMapper).updateById(captor.capture());
        // C9 部分更新载体：仅携 id + 变更字段
        assertEquals(workspaceId, captor.getValue().getId());
        assertEquals("QA 改名", captor.getValue().getName());
        assertNull(captor.getValue().getStatus());
    }

    @Test
    void updateWorkspace_notAdmin_throws() {
        when(workspaceMapper.selectById(workspaceId)).thenReturn(workspace());
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> contextService.updateWorkspace(userId, workspaceId, new WorkspaceUpdateReqDTO()));
        verify(workspaceMapper, never()).updateById(org.mockito.ArgumentMatchers.<Workspace>any());
    }

    @Test
    void updateWorkspace_duplicateName_throws() {
        stubBase();
        Workspace other = workspace();
        other.setId(UUID.fromString("00000000-0000-0000-0000-0000000000dd"));
        when(workspaceMapper.findByName("QA 改名")).thenReturn(other);

        WorkspaceUpdateReqDTO req = new WorkspaceUpdateReqDTO();
        req.setName("QA 改名");

        assertThrows(ServiceException.class,
                () -> contextService.updateWorkspace(userId, workspaceId, req));
    }

    // ========== setDefaultProject ==========

    @Test
    void setDefaultProject_success_updatesDefaultProjectId() {
        stubBase();
        stubCounts();
        when(projectMapper.selectById(projectId)).thenReturn(activeProject());

        WorkspaceDefaultProjectReqDTO req = new WorkspaceDefaultProjectReqDTO();
        req.setProjectId(projectId);
        WorkspaceContextRespDTO result = contextService.setDefaultProject(userId, workspaceId, req);

        assertEquals(projectId, result.getDefaultProjectId());
        assertEquals("P1", result.getDefaultProjectName());
        verify(workspaceUserMapper).updateDefaultProjectId(workspaceUserId, projectId);
    }

    @Test
    void setDefaultProject_clearDefault_whenProjectIdNull() {
        stubBase();
        stubCounts();

        WorkspaceContextRespDTO result = contextService.setDefaultProject(userId, workspaceId, new WorkspaceDefaultProjectReqDTO());

        assertNull(result.getDefaultProjectId());
        assertNull(result.getDefaultProjectName());
        verify(workspaceUserMapper).updateDefaultProjectId(workspaceUserId, null);
    }

    @Test
    void setDefaultProject_projectOfOtherWorkspace_throws() {
        stubBase();
        Project project = activeProject();
        project.setWorkspaceId(UUID.fromString("00000000-0000-0000-0000-0000000000dd"));
        when(projectMapper.selectById(projectId)).thenReturn(project);

        WorkspaceDefaultProjectReqDTO req = new WorkspaceDefaultProjectReqDTO();
        req.setProjectId(projectId);

        assertThrows(ServiceException.class,
                () -> contextService.setDefaultProject(userId, workspaceId, req));
        verify(workspaceUserMapper, never()).updateDefaultProjectId(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void setDefaultProject_inactiveProject_throws() {
        stubBase();
        Project project = activeProject();
        project.setStatus(Constants.Status.ARCHIVED);
        when(projectMapper.selectById(projectId)).thenReturn(project);

        WorkspaceDefaultProjectReqDTO req = new WorkspaceDefaultProjectReqDTO();
        req.setProjectId(projectId);

        assertThrows(ServiceException.class,
                () -> contextService.setDefaultProject(userId, workspaceId, req));
    }

    private Project activeProject() {
        Project project = new Project();
        project.setId(projectId);
        project.setWorkspaceId(workspaceId);
        project.setName("P1");
        project.setStatus(Constants.Status.ACTIVE);
        return project;
    }
}