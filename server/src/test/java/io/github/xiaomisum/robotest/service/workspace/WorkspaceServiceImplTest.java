package io.github.xiaomisum.robotest.service.workspace;

import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceRespDTO;
import io.github.xiaomisum.robotest.model.entity.workspace.Workspace;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceImplTest {

    @Mock
    private WorkspaceMapper workspaceMapper;
    @Mock
    private WorkspaceUserMapper workspaceUserMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ProjectMapper projectMapper;

    @InjectMocks
    private WorkspaceServiceImpl workspaceService;

    private UUID workspaceId;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        workspace = new Workspace();
        workspace.setId(workspaceId);
        workspace.setName("WS 1");
        workspace.setStatus("active");
    }

    // ========== getWorkspaceDetail ==========

    @Test
    void getWorkspaceDetail_countsProjects() {
        when(workspaceMapper.selectById(workspaceId)).thenReturn(workspace);
        when(workspaceUserMapper.countByWorkspaceId(workspaceId)).thenReturn(3L);
        when(projectMapper.countByWorkspaceId(workspaceId)).thenReturn(5L);

        WorkspaceRespDTO result = workspaceService.getWorkspaceDetail(workspaceId);

        assertEquals(3L, result.getMemberCount());
        // projectCount 曾被硬编码为 0，须来自 project 表真实统计
        assertEquals(5L, result.getProjectCount());
    }

    @Test
    void getWorkspaceDetail_notFound_throws() {
        when(workspaceMapper.selectById(workspaceId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> workspaceService.getWorkspaceDetail(workspaceId));
    }

    // ========== getWorkspacePage ==========

    @Test
    void getWorkspacePage_countsProjects() {
        PageResult<Workspace> page = new PageResult<>(List.of(workspace), 1L);
        doReturn(page).when(workspaceMapper).findPage(any(PageParam.class), any(), any());
        when(workspaceUserMapper.countByWorkspaceId(workspaceId)).thenReturn(2L);
        when(projectMapper.countByWorkspaceId(workspaceId)).thenReturn(7L);

        PageResult<WorkspaceRespDTO> result = workspaceService.getWorkspacePage(null, null, 1, 20);

        assertEquals(1, result.getList().size());
        assertEquals(2L, result.getList().get(0).getMemberCount());
        assertEquals(7L, result.getList().get(0).getProjectCount());
    }
}
