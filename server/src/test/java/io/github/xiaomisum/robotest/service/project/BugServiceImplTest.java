package io.github.xiaomisum.robotest.service.project;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;
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
        // LambdaUpdateWrapper 解析实体列名依赖 TableInfo 缓存，纯 Mockito 环境无 starter 初始化，需手动注册
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Bug.class);
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
        doReturn(pageResult).when(bugMapper).selectPage(any(PageParam.class), any(LambdaQueryWrapper.class));

        SysUser reporter = new SysUser();
        reporter.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        reporter.setUsername("reporter");
        when(userMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000004"))).thenReturn(reporter);

        PageResult<BugListRespDTO> result = bugService.getBugPage(
                projectId, Constants.BugStatus.ACTIVE, "high", "high",
                Constants.BugType.CODE_ERROR, null, null, 1, 10);

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
        doReturn(pageResult).when(bugMapper).selectPage(any(PageParam.class), any(LambdaQueryWrapper.class));

        PageResult<BugListRespDTO> result = bugService.getBugPage(
                projectId, null, null, null, null, null, null, 1, 10);

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
        when(workspaceUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(new WorkspaceUser());

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
        verify(bugMapper, never()).update(any(), any());
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
        when(workspaceUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        BugUpdateReqDTO reqDTO = new BugUpdateReqDTO();
        reqDTO.setAssigneeId(assigneeId);

        assertThrows(ServiceException.class,
                () -> bugService.updateBug(bugId, userId, reqDTO));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).update(any(), any());
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
        when(userMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000004"))).thenReturn(reporter);

        SysUser assignee = new SysUser();
        assignee.setId(UUID.fromString("00000000-0000-0000-0000-000000000005"));
        assignee.setUsername("assignee");
        when(userMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000005"))).thenReturn(assignee);

        when(bugLogMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

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

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapperX<Bug>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapperX.class);
        verify(bugMapper).update(isNull(), captor.capture());
        var values = captor.getValue().getParamNameValuePairs().values();
        assertTrue(values.contains(Constants.BugStatus.RESOLVED));
        assertTrue(values.contains(Constants.BugResolution.FIXED));
        assertTrue(values.contains(userId));
        assertTrue(values.contains(Boolean.TRUE));
        assertTrue(captor.getValue().getSqlSet().contains("resolved_at"));
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
        verify(bugMapper, never()).update(any(), any());
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
        verify(bugMapper, never()).update(any(), any());
    }

    @Test
    void resolveBug_duplicateWithoutOriginal_throws() {
        Bug bug = activeBug();
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        BugStatusChangeReqDTO reqDTO = statusChangeReq(Constants.BugStatus.RESOLVED, null);
        reqDTO.setResolution(Constants.BugResolution.DUPLICATE);

        assertThrows(ServiceException.class,
                () -> bugService.changeBugStatus(bugId, userId, reqDTO));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).update(any(), any());
    }

    @Test
    void resolveBug_duplicateOfSelf_throws() {
        Bug bug = activeBug();
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        BugStatusChangeReqDTO reqDTO = statusChangeReq(Constants.BugStatus.RESOLVED, null);
        reqDTO.setResolution(Constants.BugResolution.DUPLICATE);
        reqDTO.setDuplicateOfBugId(bugId);

        assertThrows(ServiceException.class,
                () -> bugService.changeBugStatus(bugId, userId, reqDTO));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).update(any(), any());
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

        BugStatusChangeReqDTO reqDTO = statusChangeReq(Constants.BugStatus.RESOLVED, null);
        reqDTO.setResolution(Constants.BugResolution.DUPLICATE);
        reqDTO.setDuplicateOfBugId(originalId);

        assertThrows(ServiceException.class,
                () -> bugService.changeBugStatus(bugId, userId, reqDTO));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).update(any(), any());
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

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapperX<Bug>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapperX.class);
        verify(bugMapper).update(isNull(), captor.capture());
        var values = captor.getValue().getParamNameValuePairs().values();
        assertTrue(values.contains(Constants.BugResolution.DUPLICATE));
        assertTrue(values.contains(originalId));
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
        verify(bugMapper, never()).update(any(), any());
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

        // updateById 会忽略 null 字段导致清空静默失效，重开必须走 wrapper 显式 set null
        verify(bugMapper, never()).updateById(any(Bug.class));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapperX<Bug>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapperX.class);
        verify(bugMapper).update(isNull(), captor.capture());
        var values = captor.getValue().getParamNameValuePairs().values();
        assertTrue(values.contains(Constants.BugStatus.ACTIVE));
        assertTrue(values.contains(2));
        String sqlSet = captor.getValue().getSqlSet();
        assertTrue(sqlSet.contains("last_reopened_at"));
        // 解决/关闭信息的清空列必须出现在 SET 子句中
        assertTrue(sqlSet.contains("resolution"));
        assertTrue(sqlSet.contains("duplicate_of_bug_id"));
        assertTrue(sqlSet.contains("resolved_by"));
        assertTrue(sqlSet.contains("resolved_at"));
        assertTrue(sqlSet.contains("closed_by"));
        assertTrue(sqlSet.contains("closed_at"));
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
        verify(bugMapper, never()).update(any(), any());
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
        verify(bugMapper, never()).update(any(), any());
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
        verify(bugMapper, never()).update(any(), any());
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
        verify(bugMapper, never()).update(any(), any());
    }

    @Test
    void confirmBug_notActive_throws() {
        Bug bug = activeBug();
        bug.setStatus(Constants.BugStatus.RESOLVED);
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        assertThrows(ServiceException.class,
                () -> bugService.confirmBug(bugId, userId));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).update(any(), any());
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
        verify(bugMapper, never()).update(any(), any());
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
        when(workspaceUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> bugService.assignBug(bugId, userId, assigneeId));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).update(any(), any());
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
        when(workspaceUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(new WorkspaceUser());
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

        when(bugMapper.selectList(any(LambdaQueryWrapper.class)))
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
        when(bugMapper.selectList(any(LambdaQueryWrapper.class)))
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

        when(bugLogMapper.selectList(any(LambdaQueryWrapper.class)))
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
