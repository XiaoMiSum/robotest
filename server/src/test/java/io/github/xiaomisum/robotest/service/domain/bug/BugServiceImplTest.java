package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.convert.BugConvertMapper;
import io.github.xiaomisum.robotest.model.convert.BugConvertMapperImpl;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugStatusChangeReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugUpdateReqDTO;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.bug.BugLog;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.bug.BugLogMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import io.github.xiaomisum.robotest.service.project.ProjectActivityService;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BugServiceImplTest {

    @Mock
    private BugMapper bugMapper;
    @Mock
    private BugLogMapper bugLogMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private WorkspaceUserMapper workspaceUserMapper;
    @Mock
    private ProjectModuleMapper projectModuleMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ProjectAccessGuard projectAccessGuard;

    @Mock
    private BugStatusChangeService bugStatusChangeService;
    @Mock
    private ProjectActivityService projectActivityService;
    @Spy
    private BugConvertMapper bugConvertMapper = new BugConvertMapperImpl();

    @InjectMocks
    private BugServiceImpl bugService;

    private UUID projectId;
    private UUID userId;
    private UUID bugId;

    @BeforeEach
    void setUp() {
        projectId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        userId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        bugId = UUID.fromString("00000000-0000-0000-0000-000000000003");
    }

    private BugStatusChangeReqDTO statusChangeReq(String status, String comment) {
        BugStatusChangeReqDTO reqDTO = new BugStatusChangeReqDTO();
        reqDTO.setStatus(status);
        reqDTO.setComment(comment);
        return reqDTO;
    }

    private Bug activeBug() {
        Bug bug = new Bug();
        bug.setId(bugId);
        bug.setProjectId(projectId);
        bug.setStatus(Constants.BugStatus.ACTIVE);
        return bug;
    }

    // ========== createBug ==========

    @Test
    void createBug_success() {
        doAnswer(inv -> {
            ((Bug) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(bugMapper).insert(any(Bug.class));
        doAnswer(inv -> {
            ((BugLog) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(bugLogMapper).insert(any(BugLog.class));

        Project project = new Project();
        project.setId(projectId);
        project.setWorkspaceId(UUID.fromString("00000000-0000-0000-0000-000000000009"));
        when(projectMapper.selectById(projectId)).thenReturn(project);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(
                UUID.fromString("00000000-0000-0000-0000-000000000009"),
                UUID.fromString("00000000-0000-0000-0000-000000000005"))).thenReturn(new WorkspaceUser());

        BugCreateReqDTO reqDTO = new BugCreateReqDTO();
        reqDTO.setTitle("New Bug");
        reqDTO.setSeverity("high");
        reqDTO.setPriority("high");
        reqDTO.setBugType(Constants.BugType.CODE_ERROR);
        reqDTO.setReproSteps("## 步骤\n1. 打开页面");
        reqDTO.setAssigneeId(UUID.fromString("00000000-0000-0000-0000-000000000005"));
        reqDTO.setRelatedCaseId(UUID.fromString("00000000-0000-0000-0000-000000000006"));
        reqDTO.setRelatedPlanId(UUID.fromString("00000000-0000-0000-0000-000000000007"));

        String result = bugService.createBug(projectId, userId, reqDTO);

        assertNotNull(result);
        ArgumentCaptor<Bug> captor = ArgumentCaptor.forClass(Bug.class);
        verify(bugMapper).insert(captor.capture());
        assertEquals(Constants.BugStatus.ACTIVE, captor.getValue().getStatus());
        assertEquals(Boolean.FALSE, captor.getValue().getConfirmed());
        assertEquals(0, captor.getValue().getReopenCount());
        verify(bugLogMapper).insert(any(BugLog.class));

        // 向量写入解耦为领域事件：缺陷域只发布，消费在 service.ai（06 §3.1.1）
        ArgumentCaptor<BugChangedEvent> eventCaptor = ArgumentCaptor.forClass(BugChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(BugChangeOp.CREATED, eventCaptor.getValue().op());
        assertEquals(captor.getValue().getId(), eventCaptor.getValue().bugId());
    }

    @Test
    void createBug_invalidBugType_throws() {
        BugCreateReqDTO reqDTO = new BugCreateReqDTO();
        reqDTO.setTitle("New Bug");
        reqDTO.setBugType("not_a_type");

        assertThrows(ServiceException.class,
                () -> bugService.createBug(projectId, userId, reqDTO));
        verify(bugMapper, never()).insert(any(Bug.class));
    }

    @Test
    void createBug_moduleNotInProject_throws() {
        UUID moduleId = UUID.fromString("00000000-0000-0000-0000-000000000008");
        ProjectModule module = new ProjectModule();
        module.setId(moduleId);
        module.setProjectId(UUID.fromString("00000000-0000-0000-0000-000000000099"));
        when(projectModuleMapper.selectById(moduleId)).thenReturn(module);

        BugCreateReqDTO reqDTO = new BugCreateReqDTO();
        reqDTO.setTitle("New Bug");
        reqDTO.setBugType(Constants.BugType.CODE_ERROR);
        reqDTO.setModuleId(moduleId);

        assertThrows(ServiceException.class,
                () -> bugService.createBug(projectId, userId, reqDTO));
        verify(bugMapper, never()).insert(any(Bug.class));
    }

    // ========== updateBug ==========

    @Test
    void updateBug_success() {
        Bug bug = activeBug();
        bug.setTitle("Old Title");
        bug.setSeverity("low");
        bug.setPriority("low");

        when(bugMapper.selectById(bugId)).thenReturn(bug);

        BugUpdateReqDTO reqDTO = new BugUpdateReqDTO();
        reqDTO.setTitle("New Title");
        reqDTO.setSeverity("critical");
        reqDTO.setBugType(Constants.BugType.PERFORMANCE);
        reqDTO.setReproSteps("updated steps");

        bugService.updateBug(bugId, userId, reqDTO);

        ArgumentCaptor<Bug> captor = ArgumentCaptor.forClass(Bug.class);
        verify(bugMapper).updateById(captor.capture());
        assertEquals(Constants.BugType.PERFORMANCE, captor.getValue().getBugType());
        assertEquals("updated steps", captor.getValue().getReproSteps());
        verify(bugLogMapper).insert(any(BugLog.class));

        // 标题/重现步骤变更才触发向量事件（hash 去重）
        ArgumentCaptor<BugChangedEvent> eventCaptor = ArgumentCaptor.forClass(BugChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(BugChangeOp.UPDATED, eventCaptor.getValue().op());
        assertEquals(bugId, eventCaptor.getValue().bugId());
    }

    @Test
    void updateBug_notFound_throws() {
        when(bugMapper.selectById(bugId)).thenReturn(null);

        BugUpdateReqDTO reqDTO = new BugUpdateReqDTO();
        reqDTO.setTitle("New Title");

        assertThrows(ServiceException.class,
                () -> bugService.updateBug(bugId, userId, reqDTO));
    }

    @Test
    void updateBug_closedBug_throws() {
        Bug bug = activeBug();
        bug.setStatus(Constants.BugStatus.CLOSED);
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        BugUpdateReqDTO reqDTO = new BugUpdateReqDTO();
        reqDTO.setTitle("New Title");

        assertThrows(ServiceException.class,
                () -> bugService.updateBug(bugId, userId, reqDTO));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt(), any());
    }

    @Test
    void updateBug_assigneeNotInWorkspace_throws() {
        UUID assigneeId = UUID.fromString("00000000-0000-0000-0000-000000000005");
        Bug bug = activeBug();
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        Project project = new Project();
        project.setId(projectId);
        project.setWorkspaceId(UUID.fromString("00000000-0000-0000-0000-000000000009"));
        when(projectMapper.selectById(projectId)).thenReturn(project);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(
                UUID.fromString("00000000-0000-0000-0000-000000000009"), assigneeId)).thenReturn(null);

        BugUpdateReqDTO reqDTO = new BugUpdateReqDTO();
        reqDTO.setAssigneeId(assigneeId);

        assertThrows(ServiceException.class,
                () -> bugService.updateBug(bugId, userId, reqDTO));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt(), any());
    }

    @Test
    void updateBug_setRelation_success() {
        Bug bug = activeBug();
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        UUID caseId = UUID.fromString("00000000-0000-0000-0000-000000000006");
        UUID planId = UUID.fromString("00000000-0000-0000-0000-000000000007");
        BugUpdateReqDTO reqDTO = new BugUpdateReqDTO();
        reqDTO.setRelatedCaseId(caseId.toString());
        reqDTO.setRelatedPlanId(planId.toString());

        bugService.updateBug(bugId, userId, reqDTO);

        ArgumentCaptor<Bug> captor = ArgumentCaptor.forClass(Bug.class);
        verify(bugMapper).updateById(captor.capture());
        assertEquals(caseId, captor.getValue().getRelatedCaseId());
        assertEquals(planId, captor.getValue().getRelatedPlanId());
        verify(bugMapper, never()).clearRelationById(any(), anyBoolean(), anyBoolean());
        // 仅关联字段变更不发向量事件
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void updateBug_clearRelation_success() {
        Bug bug = activeBug();
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        // 空串语义 = 清空关联
        BugUpdateReqDTO reqDTO = new BugUpdateReqDTO();
        reqDTO.setRelatedCaseId("");
        reqDTO.setRelatedPlanId("");

        bugService.updateBug(bugId, userId, reqDTO);

        ArgumentCaptor<Bug> captor = ArgumentCaptor.forClass(Bug.class);
        verify(bugMapper).updateById(captor.capture());
        assertNull(captor.getValue().getRelatedCaseId());
        assertNull(captor.getValue().getRelatedPlanId());
        verify(bugMapper).clearRelationById(bugId, true, true);
    }

    @Test
    void updateBug_relationNotProvided_untouched() {
        Bug bug = activeBug();
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        // null 语义 = 不修改关联
        BugUpdateReqDTO reqDTO = new BugUpdateReqDTO();
        reqDTO.setTitle("New Title");

        bugService.updateBug(bugId, userId, reqDTO);

        ArgumentCaptor<Bug> captor = ArgumentCaptor.forClass(Bug.class);
        verify(bugMapper).updateById(captor.capture());
        assertNull(captor.getValue().getRelatedCaseId());
        assertNull(captor.getValue().getRelatedPlanId());
        verify(bugMapper, never()).clearRelationById(any(), anyBoolean(), anyBoolean());
    }

    @Test
    void updateBug_invalidRelationId_throws() {
        Bug bug = activeBug();
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        BugUpdateReqDTO reqDTO = new BugUpdateReqDTO();
        reqDTO.setRelatedCaseId("not-a-uuid");

        assertThrows(ServiceException.class,
                () -> bugService.updateBug(bugId, userId, reqDTO));
        verify(bugMapper, never()).updateById(any(Bug.class));
    }

    // ========== changeBugStatus：委托验证 ==========

    @Test
    void changeBugStatus_delegatesToStatusChangeService() {
        Bug bug = activeBug();
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        BugStatusChangeReqDTO reqDTO = statusChangeReq(Constants.BugStatus.RESOLVED, "修复完成");
        reqDTO.setResolution(Constants.BugResolution.FIXED);

        bugService.changeBugStatus(bugId, userId, reqDTO);

        verify(bugStatusChangeService).changeBugStatus(bug, userId, reqDTO);
    }

    @Test
    void changeBugStatus_notFound_throws() {
        when(bugMapper.selectById(bugId)).thenReturn(null);

        BugStatusChangeReqDTO reqDTO = statusChangeReq(Constants.BugStatus.RESOLVED, "ok");
        reqDTO.setResolution(Constants.BugResolution.FIXED);

        assertThrows(ServiceException.class,
                () -> bugService.changeBugStatus(bugId, userId, reqDTO));
        verify(bugStatusChangeService, never()).changeBugStatus(any(), any(), any());
    }

    // ========== confirmBug ==========

    @Test
    void confirmBug_success() {
        Bug bug = activeBug();
        bug.setConfirmed(false);
        when(bugMapper.selectById(bugId)).thenReturn(bug);
        doAnswer(inv -> {
            ((BugLog) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(bugLogMapper).insert(any(BugLog.class));

        bugService.confirmBug(bugId, userId);

        ArgumentCaptor<Bug> captor = ArgumentCaptor.forClass(Bug.class);
        verify(bugMapper).updateById(captor.capture());
        assertEquals(Boolean.TRUE, captor.getValue().getConfirmed());
        verify(bugLogMapper).insert(any(BugLog.class));
    }

    @Test
    void confirmBug_alreadyConfirmed_throws() {
        Bug bug = activeBug();
        bug.setConfirmed(true);
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        assertThrows(ServiceException.class,
                () -> bugService.confirmBug(bugId, userId));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt(), any());
    }

    @Test
    void confirmBug_notActive_throws() {
        Bug bug = activeBug();
        bug.setStatus(Constants.BugStatus.RESOLVED);
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        assertThrows(ServiceException.class,
                () -> bugService.confirmBug(bugId, userId));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt(), any());
    }

    @Test
    void confirmBug_notFound_throws() {
        when(bugMapper.selectById(bugId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> bugService.confirmBug(bugId, userId));
    }

    // ========== assignBug ==========

    @Test
    void assignBug_success() {
        Bug bug = new Bug();
        bug.setId(bugId);
        bug.setReporterId(userId);

        when(bugMapper.selectById(bugId)).thenReturn(bug);

        SysUser assignee = new SysUser();
        assignee.setId(UUID.fromString("00000000-0000-0000-0000-000000000005"));
        assignee.setUsername("assignee");
        when(userMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000005"))).thenReturn(assignee);
        doAnswer(inv -> {
            ((BugLog) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(bugLogMapper).insert(any(BugLog.class));

        bugService.assignBug(bugId, userId, UUID.fromString("00000000-0000-0000-0000-000000000005"));

        verify(bugMapper).updateById(any(Bug.class));
        verify(bugLogMapper).insert(any(BugLog.class));
    }

    @Test
    void assignBug_notFound_throws() {
        when(bugMapper.selectById(bugId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> bugService.assignBug(bugId, userId, UUID.fromString("00000000-0000-0000-0000-000000000005")));
    }

    @Test
    void assignBug_closedBug_throws() {
        UUID assigneeId = UUID.fromString("00000000-0000-0000-0000-000000000005");
        Bug bug = activeBug();
        bug.setStatus(Constants.BugStatus.CLOSED);
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        assertThrows(ServiceException.class,
                () -> bugService.assignBug(bugId, userId, assigneeId));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt(), any());
    }

    @Test
    void assignBug_assigneeNotFound_throws() {
        Bug bug = new Bug();
        bug.setId(bugId);
        when(bugMapper.selectById(bugId)).thenReturn(bug);
        when(userMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000005"))).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> bugService.assignBug(bugId, userId, UUID.fromString("00000000-0000-0000-0000-000000000005")));
    }

    @Test
    void assignBug_assigneeNotInWorkspace_throws() {
        UUID assigneeId = UUID.fromString("00000000-0000-0000-0000-000000000005");
        Bug bug = new Bug();
        bug.setId(bugId);
        bug.setProjectId(projectId);
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        SysUser assignee = new SysUser();
        assignee.setId(assigneeId);
        assignee.setUsername("assignee");
        when(userMapper.selectById(assigneeId)).thenReturn(assignee);

        Project project = new Project();
        project.setId(projectId);
        project.setWorkspaceId(UUID.fromString("00000000-0000-0000-0000-000000000009"));
        when(projectMapper.selectById(projectId)).thenReturn(project);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(
                UUID.fromString("00000000-0000-0000-0000-000000000009"), assigneeId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> bugService.assignBug(bugId, userId, assigneeId));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt(), any());
    }

    @Test
    void assignBug_assigneeInWorkspace_success() {
        UUID assigneeId = UUID.fromString("00000000-0000-0000-0000-000000000005");
        Bug bug = new Bug();
        bug.setId(bugId);
        bug.setProjectId(projectId);
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        SysUser assignee = new SysUser();
        assignee.setId(assigneeId);
        assignee.setUsername("assignee");
        when(userMapper.selectById(assigneeId)).thenReturn(assignee);

        Project project = new Project();
        project.setId(projectId);
        project.setWorkspaceId(UUID.fromString("00000000-0000-0000-0000-000000000009"));
        when(projectMapper.selectById(projectId)).thenReturn(project);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(
                UUID.fromString("00000000-0000-0000-0000-000000000009"), assigneeId)).thenReturn(new WorkspaceUser());
        doAnswer(inv -> {
            ((BugLog) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(bugLogMapper).insert(any(BugLog.class));

        bugService.assignBug(bugId, userId, assigneeId);

        ArgumentCaptor<Bug> captor = ArgumentCaptor.forClass(Bug.class);
        verify(bugMapper).updateById(captor.capture());
        assertEquals(assigneeId, captor.getValue().getAssigneeId());
    }
}
