package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugStatusChangeReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugLogRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugStatisticsRespDTO;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.bug.BugLog;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.bug.BugLogMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

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
    private TestCaseModuleMapper testCaseModuleMapper;

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

    // ========== getBugPage ==========

    @Test
    void getBugPage_withFilters() {
        Bug bug = new Bug();
        bug.setId(bugId);
        bug.setTitle("Test Bug");
        bug.setSeverity("high");
        bug.setPriority("high");
        bug.setStatus(Constants.BugStatus.ACTIVE);
        bug.setBugType(Constants.BugType.CODE_ERROR);
        bug.setReporterId(UUID.fromString("00000000-0000-0000-0000-000000000004"));

        PageResult<Bug> pageResult = new PageResult<>(List.of(bug), 1L);
        doReturn(pageResult).when(bugMapper).findPage(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        SysUser reporter = new SysUser();
        reporter.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        reporter.setUsername("reporter");
        when(userMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000004"))).thenReturn(reporter);

        PageResult<BugListRespDTO> result = bugService.getBugPage(
                projectId, Constants.BugStatus.ACTIVE, "high", "high",
                Constants.BugType.CODE_ERROR, null, null, null, null, null, 1, 10);

        assertNotNull(result);
        assertEquals(1, result.getList().size());
        assertEquals(1L, result.getTotal());
        assertEquals("Test Bug", result.getList().get(0).getTitle());
        assertEquals(Constants.BugType.CODE_ERROR, result.getList().get(0).getBugType());
        assertEquals("reporter", result.getList().get(0).getReporter().getName());
    }

    @Test
    void getBugPage_emptyResult() {
        PageResult<Bug> pageResult = new PageResult<>(Collections.emptyList(), 0L);
        doReturn(pageResult).when(bugMapper).findPage(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        PageResult<BugListRespDTO> result = bugService.getBugPage(
                projectId, null, null, null, null, null, null, null, null, null, 1, 10);

        assertNotNull(result);
        assertTrue(result.getList().isEmpty());
        assertEquals(0L, result.getTotal());
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
        TestCaseModule module = new TestCaseModule();
        module.setId(moduleId);
        module.setProjectId(UUID.fromString("00000000-0000-0000-0000-000000000099"));
        when(testCaseModuleMapper.selectById(moduleId)).thenReturn(module);

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
        verify(bugMapper, never()).resolveById(any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt());
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
        verify(bugMapper, never()).resolveById(any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt());
    }

    // ========== getBugDetail ==========

    @Test
    void getBugDetail_success() {
        Bug bug = activeBug();
        bug.setTitle("Detail Bug");
        bug.setSeverity("fatal");
        bug.setPriority("high");
        bug.setBugType(Constants.BugType.CODE_ERROR);
        bug.setReproSteps("steps");
        bug.setConfirmed(true);
        bug.setReopenCount(2);
        bug.setReporterId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        bug.setAssigneeId(UUID.fromString("00000000-0000-0000-0000-000000000005"));

        when(bugMapper.selectById(bugId)).thenReturn(bug);

        SysUser reporter = new SysUser();
        reporter.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        reporter.setUsername("reporter");

        SysUser assignee = new SysUser();
        assignee.setId(UUID.fromString("00000000-0000-0000-0000-000000000005"));
        assignee.setUsername("assignee");

        // resolvedBy/closedBy 为 null 时服务会以 null 入参查询，需用 Answer 兼容
        when(userMapper.selectById(any())).thenAnswer(inv -> {
            Object id = inv.getArgument(0);
            if (reporter.getId().equals(id)) {
                return reporter;
            }
            if (assignee.getId().equals(id)) {
                return assignee;
            }
            return null;
        });

        when(bugLogMapper.findRecentLogs(bugId, 10)).thenReturn(Collections.emptyList());

        BugDetailRespDTO result = bugService.getBugDetail(bugId);

        assertNotNull(result);
        assertEquals("Detail Bug", result.getTitle());
        assertEquals("fatal", result.getSeverity());
        assertEquals(Constants.BugType.CODE_ERROR, result.getBugType());
        assertEquals("steps", result.getReproSteps());
        assertEquals(Boolean.TRUE, result.getConfirmed());
        assertEquals(2, result.getReopenCount());
        assertEquals("reporter", result.getReporter().getName());
        assertEquals("assignee", result.getAssignee().getName());
        assertNotNull(result.getRecentLogs());
    }

    @Test
    void getBugDetail_notFound_throws() {
        when(bugMapper.selectById(bugId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> bugService.getBugDetail(bugId));
    }

    // ========== changeBugStatus：解决 ==========

    @Test
    void resolveBug_success() {
        Bug bug = activeBug();
        when(bugMapper.selectById(bugId)).thenReturn(bug);
        doAnswer(inv -> {
            ((BugLog) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(bugLogMapper).insert(any(BugLog.class));

        BugStatusChangeReqDTO reqDTO = statusChangeReq(Constants.BugStatus.RESOLVED, "修复完成");
        reqDTO.setResolution(Constants.BugResolution.FIXED);

        bugService.changeBugStatus(bugId, userId, reqDTO);

        // 状态置位与解决信息写入已封装进 Mapper default 方法，这里只验证入参正确
        verify(bugMapper).resolveById(bugId, userId, Constants.BugResolution.FIXED, null);
        verify(bugLogMapper).insert(any(BugLog.class));
    }

    @Test
    void resolveBug_withoutResolution_throws() {
        Bug bug = activeBug();
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        BugStatusChangeReqDTO reqDTO = statusChangeReq(Constants.BugStatus.RESOLVED, null);

        assertThrows(ServiceException.class,
                () -> bugService.changeBugStatus(bugId, userId, reqDTO));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt());
    }

    @Test
    void resolveBug_invalidResolution_throws() {
        Bug bug = activeBug();
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        BugStatusChangeReqDTO reqDTO = statusChangeReq(Constants.BugStatus.RESOLVED, null);
        reqDTO.setResolution("not_a_resolution");

        assertThrows(ServiceException.class,
                () -> bugService.changeBugStatus(bugId, userId, reqDTO));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt());
    }

    @Test
    void resolveBug_withoutComment_throws() {
        Bug bug = activeBug();
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        BugStatusChangeReqDTO reqDTO = statusChangeReq(Constants.BugStatus.RESOLVED, null);
        reqDTO.setResolution(Constants.BugResolution.FIXED);

        assertThrows(ServiceException.class,
                () -> bugService.changeBugStatus(bugId, userId, reqDTO));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt());
    }

    @Test
    void resolveBug_duplicateWithoutOriginal_throws() {
        Bug bug = activeBug();
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        BugStatusChangeReqDTO reqDTO = statusChangeReq(Constants.BugStatus.RESOLVED, "与已有缺陷重复");
        reqDTO.setResolution(Constants.BugResolution.DUPLICATE);

        assertThrows(ServiceException.class,
                () -> bugService.changeBugStatus(bugId, userId, reqDTO));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt());
    }

    @Test
    void resolveBug_duplicateOfSelf_throws() {
        Bug bug = activeBug();
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        BugStatusChangeReqDTO reqDTO = statusChangeReq(Constants.BugStatus.RESOLVED, "与已有缺陷重复");
        reqDTO.setResolution(Constants.BugResolution.DUPLICATE);
        reqDTO.setDuplicateOfBugId(bugId);

        assertThrows(ServiceException.class,
                () -> bugService.changeBugStatus(bugId, userId, reqDTO));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt());
    }

    @Test
    void resolveBug_duplicateOfOtherProject_throws() {
        Bug bug = activeBug();
        UUID originalId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        Bug original = new Bug();
        original.setId(originalId);
        original.setProjectId(UUID.fromString("00000000-0000-0000-0000-000000000099"));

        when(bugMapper.selectById(bugId)).thenReturn(bug);
        when(bugMapper.selectById(originalId)).thenReturn(original);

        BugStatusChangeReqDTO reqDTO = statusChangeReq(Constants.BugStatus.RESOLVED, "与已有缺陷重复");
        reqDTO.setResolution(Constants.BugResolution.DUPLICATE);
        reqDTO.setDuplicateOfBugId(originalId);

        assertThrows(ServiceException.class,
                () -> bugService.changeBugStatus(bugId, userId, reqDTO));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt());
    }

    @Test
    void resolveBug_duplicateValid_success() {
        Bug bug = activeBug();
        UUID originalId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        Bug original = new Bug();
        original.setId(originalId);
        original.setProjectId(projectId);

        when(bugMapper.selectById(bugId)).thenReturn(bug);
        when(bugMapper.selectById(originalId)).thenReturn(original);
        doAnswer(inv -> {
            ((BugLog) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(bugLogMapper).insert(any(BugLog.class));

        BugStatusChangeReqDTO reqDTO = statusChangeReq(Constants.BugStatus.RESOLVED, "与已有缺陷重复");
        reqDTO.setResolution(Constants.BugResolution.DUPLICATE);
        reqDTO.setDuplicateOfBugId(originalId);

        bugService.changeBugStatus(bugId, userId, reqDTO);

        verify(bugMapper).resolveById(bugId, userId, Constants.BugResolution.DUPLICATE, originalId);
    }

    // ========== changeBugStatus：关闭 ==========

    @Test
    void closeBug_success() {
        Bug bug = activeBug();
        bug.setStatus(Constants.BugStatus.RESOLVED);
        when(bugMapper.selectById(bugId)).thenReturn(bug);
        doAnswer(inv -> {
            ((BugLog) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(bugLogMapper).insert(any(BugLog.class));

        bugService.changeBugStatus(bugId, userId,
                statusChangeReq(Constants.BugStatus.CLOSED, "验证通过"));

        ArgumentCaptor<Bug> captor = ArgumentCaptor.forClass(Bug.class);
        verify(bugMapper).updateById(captor.capture());
        assertEquals(Constants.BugStatus.CLOSED, captor.getValue().getStatus());
        assertEquals(userId, captor.getValue().getClosedBy());
        assertNotNull(captor.getValue().getClosedAt());
    }

    @Test
    void closeBug_withoutComment_throws() {
        Bug bug = activeBug();
        bug.setStatus(Constants.BugStatus.RESOLVED);
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        assertThrows(ServiceException.class,
                () -> bugService.changeBugStatus(bugId, userId,
                        statusChangeReq(Constants.BugStatus.CLOSED, null)));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt());
    }

    // ========== changeBugStatus：拒绝 ==========

    @Test
    void rejectBug_success_assigneeSetToReporter() {
        UUID reporterId = UUID.fromString("00000000-0000-0000-0000-000000000004");
        Bug bug = activeBug();
        bug.setReporterId(reporterId);
        when(bugMapper.selectById(bugId)).thenReturn(bug);
        doAnswer(inv -> {
            ((BugLog) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(bugLogMapper).insert(any(BugLog.class));

        bugService.changeBugStatus(bugId, userId,
                statusChangeReq(Constants.BugStatus.REJECTED, "非缺陷，按设计工作"));

        ArgumentCaptor<Bug> captor = ArgumentCaptor.forClass(Bug.class);
        verify(bugMapper).updateById(captor.capture());
        assertEquals(Constants.BugStatus.REJECTED, captor.getValue().getStatus());
        assertEquals(reporterId, captor.getValue().getAssigneeId());
        verify(bugLogMapper).insert(any(BugLog.class));
    }

    @Test
    void rejectBug_withoutComment_throws() {
        Bug bug = activeBug();
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        assertThrows(ServiceException.class,
                () -> bugService.changeBugStatus(bugId, userId,
                        statusChangeReq(Constants.BugStatus.REJECTED, null)));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt());
    }

    @Test
    void rejectBug_fromResolved_throws() {
        Bug bug = activeBug();
        bug.setStatus(Constants.BugStatus.RESOLVED);
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        assertThrows(ServiceException.class,
                () -> bugService.changeBugStatus(bugId, userId,
                        statusChangeReq(Constants.BugStatus.REJECTED, "拒绝已修复缺陷")));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt());
    }

    @Test
    void rejectedBug_canBeClosed() {
        Bug bug = activeBug();
        bug.setStatus(Constants.BugStatus.REJECTED);
        when(bugMapper.selectById(bugId)).thenReturn(bug);
        doAnswer(inv -> {
            ((BugLog) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(bugLogMapper).insert(any(BugLog.class));

        bugService.changeBugStatus(bugId, userId,
                statusChangeReq(Constants.BugStatus.CLOSED, "认可拒绝，关闭缺陷"));

        ArgumentCaptor<Bug> captor = ArgumentCaptor.forClass(Bug.class);
        verify(bugMapper).updateById(captor.capture());
        assertEquals(Constants.BugStatus.CLOSED, captor.getValue().getStatus());
    }

    @Test
    void rejectedBug_canBeReopened() {
        Bug bug = activeBug();
        bug.setStatus(Constants.BugStatus.REJECTED);
        bug.setReopenCount(0);
        when(bugMapper.selectById(bugId)).thenReturn(bug);
        doAnswer(inv -> {
            ((BugLog) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(bugLogMapper).insert(any(BugLog.class));

        bugService.changeBugStatus(bugId, userId,
                statusChangeReq(Constants.BugStatus.ACTIVE, "不认可拒绝，重新激活"));

        verify(bugMapper).reopenById(bugId, 1);
    }

    // ========== changeBugStatus：重开 ==========

    @Test
    void reopenBug_incrementsCountAndClearsResolutionFields() {
        Bug bug = activeBug();
        bug.setStatus(Constants.BugStatus.CLOSED);
        bug.setReopenCount(1);
        bug.setResolution(Constants.BugResolution.FIXED);
        bug.setDuplicateOfBugId(UUID.fromString("00000000-0000-0000-0000-000000000010"));
        bug.setResolvedBy(userId);
        bug.setResolvedAt(LocalDateTime.now());
        bug.setClosedBy(userId);
        bug.setClosedAt(LocalDateTime.now());

        when(bugMapper.selectById(bugId)).thenReturn(bug);
        doAnswer(inv -> {
            ((BugLog) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(bugLogMapper).insert(any(BugLog.class));

        bugService.changeBugStatus(bugId, userId,
                statusChangeReq(Constants.BugStatus.ACTIVE, "问题复现"));

        // updateById 会忽略 null 字段导致清空静默失效，重开必须走 reopenById 显式 set null
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper).reopenById(bugId, 2);
    }

    @Test
    void reopenBug_withoutComment_throws() {
        Bug bug = activeBug();
        bug.setStatus(Constants.BugStatus.RESOLVED);
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        assertThrows(ServiceException.class,
                () -> bugService.changeBugStatus(bugId, userId,
                        statusChangeReq(Constants.BugStatus.ACTIVE, null)));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt());
    }

    // ========== changeBugStatus：非法流转 ==========

    @Test
    void changeBugStatus_activeToClosed_throws() {
        Bug bug = activeBug();
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        assertThrows(ServiceException.class,
                () -> bugService.changeBugStatus(bugId, userId,
                        statusChangeReq(Constants.BugStatus.CLOSED, "跳过解决直接关闭")));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt());
    }

    @Test
    void changeBugStatus_closedToResolved_throws() {
        Bug bug = activeBug();
        bug.setStatus(Constants.BugStatus.CLOSED);
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        BugStatusChangeReqDTO reqDTO = statusChangeReq(Constants.BugStatus.RESOLVED, null);
        reqDTO.setResolution(Constants.BugResolution.FIXED);

        assertThrows(ServiceException.class,
                () -> bugService.changeBugStatus(bugId, userId, reqDTO));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt());
    }

    @Test
    void changeBugStatus_notFound_throws() {
        when(bugMapper.selectById(bugId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> bugService.changeBugStatus(bugId, userId,
                        statusChangeReq(Constants.BugStatus.RESOLVED, null)));
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
        verify(bugMapper, never()).resolveById(any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt());
    }

    @Test
    void confirmBug_notActive_throws() {
        Bug bug = activeBug();
        bug.setStatus(Constants.BugStatus.RESOLVED);
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        assertThrows(ServiceException.class,
                () -> bugService.confirmBug(bugId, userId));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt());
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
        verify(bugMapper, never()).resolveById(any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt());
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
        verify(bugMapper, never()).resolveById(any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt());
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

    // ========== getBugStatistics ==========

    @Test
    void getBugStatistics_groupsCorrectly() {
        Bug b1 = new Bug();
        b1.setId(UUID.randomUUID());
        b1.setStatus(Constants.BugStatus.ACTIVE);
        b1.setSeverity("fatal");
        b1.setPriority("high");
        b1.setReporterId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        b1.setAssigneeId(UUID.fromString("00000000-0000-0000-0000-000000000005"));

        Bug b2 = new Bug();
        b2.setId(UUID.randomUUID());
        b2.setStatus(Constants.BugStatus.ACTIVE);
        b2.setSeverity("general");
        b2.setPriority("low");
        b2.setReporterId(UUID.fromString("00000000-0000-0000-0000-000000000004"));

        when(bugMapper.findByProjectId(projectId))
                .thenReturn(List.of(b1, b2));

        BugStatisticsRespDTO result = bugService.getBugStatistics(projectId);

        assertNotNull(result);
        assertEquals(2, result.getTotal());
        assertEquals(2L, result.getByStatus().get(Constants.BugStatus.ACTIVE));
        assertEquals(1L, result.getBySeverity().get("fatal"));
        assertEquals(1L, result.getBySeverity().get("general"));
        assertEquals(2L, result.getByReporter().get(UUID.fromString("00000000-0000-0000-0000-000000000004")));
    }

    @Test
    void getBugStatistics_emptyProject() {
        when(bugMapper.findByProjectId(projectId))
                .thenReturn(Collections.emptyList());

        BugStatisticsRespDTO result = bugService.getBugStatistics(projectId);

        assertNotNull(result);
        assertEquals(0, result.getTotal());
    }

    // ========== getBugLogs ==========

    @Test
    void getBugLogs_returnsLogs() {
        BugLog log = new BugLog();
        log.setId(UUID.fromString("00000000-0000-0000-0000-000000000005"));
        log.setBugId(bugId);
        log.setOperatorId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        log.setOperationType("create");
        log.setContent("Created");

        when(bugLogMapper.findByBugId(bugId))
                .thenReturn(List.of(log));

        SysUser operator = new SysUser();
        operator.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        operator.setUsername("operator");
        when(userMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000004"))).thenReturn(operator);

        List<BugLogRespDTO> result = bugService.getBugLogs(bugId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("operator", result.get(0).getOperatorName());
    }
}
