package io.github.xiaomisum.robotest.service.admin;

import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceMyRespDTO;
import io.github.xiaomisum.robotest.model.entity.workspace.Workspace;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyWorkspaceServiceImplTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private WorkspaceMapper workspaceMapper;
    @Mock
    private WorkspaceUserMapper workspaceUserMapper;
    @Mock
    private ProjectMapper projectMapper;

    @InjectMocks
    private MyWorkspaceServiceImpl myWorkspaceService;

    @Test
    void getMyWorkspacePage_countsProjects() {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID workspaceId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        WorkspaceUser wu = new WorkspaceUser();
        wu.setUserId(userId);
        wu.setWorkspaceId(workspaceId);
        wu.setWorkspaceRole(UUID.fromString("00000000-0000-0000-0000-000000000009"));

        Workspace workspace = new Workspace();
        workspace.setId(workspaceId);
        workspace.setName("WS 1");
        workspace.setStatus("active");

        doReturn(new PageResult<>(List.of(wu), 1L))
                .when(workspaceUserMapper).findPageByUserId(any(PageParam.class), any(UUID.class));
        when(workspaceMapper.listByIds(List.of(workspaceId))).thenReturn(List.of(workspace));
        when(workspaceUserMapper.countByWorkspaceId(workspaceId)).thenReturn(4L);
        when(projectMapper.countByWorkspaceId(workspaceId)).thenReturn(6L);

        PageResult<WorkspaceMyRespDTO> result = myWorkspaceService.getMyWorkspacePage(userId, 1, 12);

        assertEquals(1, result.getList().size());
        assertEquals(4L, result.getList().get(0).getMemberCount());
        // projectCount 曾被硬编码为 0，须来自 project 表真实统计
        assertEquals(6L, result.getList().get(0).getProjectCount());
    }
}
