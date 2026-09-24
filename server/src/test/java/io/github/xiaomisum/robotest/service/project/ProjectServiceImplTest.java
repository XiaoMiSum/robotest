package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.convert.ProjectConvertMapper;
import io.github.xiaomisum.robotest.model.convert.ProjectConvertMapperImpl;
import io.github.xiaomisum.robotest.model.dto.response.workspace.ProjectStatusCountsRespDTO;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.plan.TestPlanMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTest {

    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private WorkspaceUserMapper workspaceUserMapper;
    @Mock
    private TestPlanMapper testPlanMapper;
    @Spy
    private ProjectConvertMapper projectConvertMapper = new ProjectConvertMapperImpl();
    @InjectMocks
    private ProjectServiceImpl projectService;

    @Test
    void getProjectStatusCounts_appliesKeywordAndNormalizesMissingValues() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        WorkspaceUser workspaceUser = new WorkspaceUser();
        workspaceUser.setWorkspaceId(workspaceId);
        workspaceUser.setUserId(userId);
        workspaceUser.setWorkspaceRole(Constants.WorkspaceRole.ADMIN_ID);
        ProjectStatusCountsRespDTO counts = new ProjectStatusCountsRespDTO();
        counts.setActive(5L);
        counts.setArchived(null);

        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(workspaceUser);
        when(projectMapper.countStatusByWorkspaceId(workspaceId, "质量")).thenReturn(counts);

        ProjectStatusCountsRespDTO result = projectService.getProjectStatusCounts(workspaceId, userId, "  质量  ");

        assertEquals(5L, result.getActive());
        assertEquals(0L, result.getArchived());
        verify(projectMapper).countStatusByWorkspaceId(workspaceId, "质量");
    }

    @Test
    void getProjectStatusCounts_rejectsNonMember() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> projectService.getProjectStatusCounts(workspaceId, userId, null));

        assertEquals(ErrorCodeConstants.NO_PERMISSION.code(), exception.getCode());
    }
}
