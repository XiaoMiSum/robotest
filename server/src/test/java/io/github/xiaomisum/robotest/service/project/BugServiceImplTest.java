package io.github.xiaomisum.robotest.service.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.BugCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.BugUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugLogRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugStatisticsRespDTO;
import io.github.xiaomisum.robotest.model.entity.Bug;
import io.github.xiaomisum.robotest.model.entity.BugLog;
import io.github.xiaomisum.robotest.model.entity.Project;
import io.github.xiaomisum.robotest.model.entity.SysUser;
import io.github.xiaomisum.robotest.model.entity.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.BugLogMapper;
import io.github.xiaomisum.robotest.repository.BugMapper;
import io.github.xiaomisum.robotest.repository.ProjectMapper;
import io.github.xiaomisum.robotest.repository.SysUserMapper;
import io.github.xiaomisum.robotest.repository.WorkspaceUserMapper;
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

    @InjectMocks
    private BugServiceImpl bugService;

    private String projectId;
    private String userId;
    private String bugId;

    @BeforeEach
    void setUp() {
        projectId = "00000000-0000-0000-0000-000000000001";
        userId = "00000000-0000-0000-0000-000000000002";
        bugId = "00000000-0000-0000-0000-000000000003";
    }

    // ========== getBugPage ==========

    @Test
    void getBugPage_withFilters() {
        Bug bug = new Bug();
        bug.setId(UUID.fromString(bugId));
        bug.setTitle("Test Bug");
        bug.setSeverity("high");
        bug.setPriority("high");
        bug.setStatus("new");
        bug.setReporterId("00000000-0000-0000-0000-000000000004");

        PageResult<Bug> pageResult = new PageResult<>(List.of(bug), 1L);
        doReturn(pageResult).when(bugMapper).selectPage(any(PageParam.class), any(LambdaQueryWrapper.class));

        SysUser reporter = new SysUser();
        reporter.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        reporter.setUsername("reporter");
        when(userMapper.selectById("00000000-0000-0000-0000-000000000004")).thenReturn(reporter);

        PageResult<BugListRespDTO> result = bugService.getBugPage(
                projectId, "new", "high", "high", null, null, 1, 10);

        assertNotNull(result);
        assertEquals(1, result.getList().size());
        assertEquals(1L, result.getTotal());
        assertEquals("Test Bug", result.getList().get(0).getTitle());
        assertEquals("reporter", result.getList().get(0).getReporter().getName());
    }

    @Test
    void getBugPage_emptyResult() {
        PageResult<Bug> pageResult = new PageResult<>(Collections.emptyList(), 0L);
        doReturn(pageResult).when(bugMapper).selectPage(any(PageParam.class), any(LambdaQueryWrapper.class));

        PageResult<BugListRespDTO> result = bugService.getBugPage(
                projectId, null, null, null, null, null, 1, 10);

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

        BugCreateReqDTO reqDTO = new BugCreateReqDTO();
        reqDTO.setTitle("New Bug");
        reqDTO.setSeverity("high");
        reqDTO.setPriority("high");
        reqDTO.setDescription("desc");
        reqDTO.setAssigneeId(UUID.fromString("00000000-0000-0000-0000-000000000005"));
        reqDTO.setRelatedCaseId(UUID.fromString("00000000-0000-0000-0000-000000000006"));
        reqDTO.setRelatedPlanId(UUID.fromString("00000000-0000-0000-0000-000000000007"));

        String result = bugService.createBug(projectId, userId, reqDTO);

        assertNotNull(result);
        verify(bugMapper).insert(any(Bug.class));
        verify(bugLogMapper).insert(any(BugLog.class));
    }

    // ========== updateBug ==========

    @Test
    void updateBug_success() {
        Bug bug = new Bug();
        bug.setId(UUID.fromString(bugId));
        bug.setTitle("Old Title");
        bug.setSeverity("low");
        bug.setPriority("low");
        bug.setStatus("new");

        when(bugMapper.selectById(bugId)).thenReturn(bug);

        BugUpdateReqDTO reqDTO = new BugUpdateReqDTO();
        reqDTO.setTitle("New Title");
        reqDTO.setSeverity("critical");

        bugService.updateBug(bugId, userId, reqDTO);

        verify(bugMapper).updateById(any(Bug.class));
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

    // ========== getBugDetail ==========

    @Test
    void getBugDetail_success() {
        Bug bug = new Bug();
        bug.setId(UUID.fromString(bugId));
        bug.setTitle("Detail Bug");
        bug.setSeverity("fatal");
        bug.setPriority("high");
        bug.setStatus("new");
        bug.setDescription("desc");
        bug.setReporterId("00000000-0000-0000-0000-000000000004");
        bug.setAssigneeId("00000000-0000-0000-0000-000000000005");

        when(bugMapper.selectById(bugId)).thenReturn(bug);

        SysUser reporter = new SysUser();
        reporter.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        reporter.setUsername("reporter");
        when(userMapper.selectById("00000000-0000-0000-0000-000000000004")).thenReturn(reporter);

        SysUser assignee = new SysUser();
        assignee.setId(UUID.fromString("00000000-0000-0000-0000-000000000005"));
        assignee.setUsername("assignee");
        when(userMapper.selectById("00000000-0000-0000-0000-000000000005")).thenReturn(assignee);

        when(bugLogMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        BugDetailRespDTO result = bugService.getBugDetail(bugId);

        assertNotNull(result);
        assertEquals("Detail Bug", result.getTitle());
        assertEquals("fatal", result.getSeverity());
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

    // ========== changeBugStatus ==========

    @Test
    void changeBugStatus_validTransition_success() {
        Bug bug = new Bug();
        bug.setId(UUID.fromString(bugId));
        bug.setStatus(Constants.BugStatus.NEW);

        when(bugMapper.selectById(bugId)).thenReturn(bug);
        doAnswer(inv -> {
            ((BugLog) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(bugLogMapper).insert(any(BugLog.class));

        bugService.changeBugStatus(bugId, userId, Constants.BugStatus.ASSIGNED, null);

        ArgumentCaptor<Bug> captor = ArgumentCaptor.forClass(Bug.class);
        verify(bugMapper).updateById(captor.capture());
        assertEquals(Constants.BugStatus.ASSIGNED, captor.getValue().getStatus());
        verify(bugLogMapper).insert(any(BugLog.class));
    }

    @Test
    void changeBugStatus_invalidTransition_throws() {
        Bug bug = new Bug();
        bug.setId(UUID.fromString(bugId));
        bug.setStatus(Constants.BugStatus.CLOSED);

        when(bugMapper.selectById(bugId)).thenReturn(bug);

        assertThrows(ServiceException.class,
                () -> bugService.changeBugStatus(bugId, userId, Constants.BugStatus.NEW, null));
    }

    @Test
    void changeBugStatus_reopenWithoutComment_throws() {
        Bug bug = new Bug();
        bug.setId(UUID.fromString(bugId));
        bug.setStatus(Constants.BugStatus.CLOSED);

        when(bugMapper.selectById(bugId)).thenReturn(bug);

        assertThrows(ServiceException.class,
                () -> bugService.changeBugStatus(bugId, userId, Constants.BugStatus.FIXING, null));
    }

    // ========== assignBug ==========

    @Test
    void assignBug_success() {
        Bug bug = new Bug();
        bug.setId(UUID.fromString(bugId));
        bug.setReporterId(userId);

        when(bugMapper.selectById(bugId)).thenReturn(bug);

        SysUser assignee = new SysUser();
        assignee.setId(UUID.fromString("00000000-0000-0000-0000-000000000005"));
        assignee.setUsername("assignee");
        when(userMapper.selectById("00000000-0000-0000-0000-000000000005")).thenReturn(assignee);
        doAnswer(inv -> {
            ((BugLog) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(bugLogMapper).insert(any(BugLog.class));

        bugService.assignBug(bugId, userId, "00000000-0000-0000-0000-000000000005");

        verify(bugMapper).updateById(any(Bug.class));
        verify(bugLogMapper).insert(any(BugLog.class));
    }

    @Test
    void assignBug_notFound_throws() {
        when(bugMapper.selectById(bugId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> bugService.assignBug(bugId, userId, "00000000-0000-0000-0000-000000000005"));
    }

    @Test
    void assignBug_assigneeNotFound_throws() {
        Bug bug = new Bug();
        bug.setId(UUID.fromString(bugId));
        when(bugMapper.selectById(bugId)).thenReturn(bug);
        when(userMapper.selectById("00000000-0000-0000-0000-000000000005")).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> bugService.assignBug(bugId, userId, "00000000-0000-0000-0000-000000000005"));
    }

    // ========== getBugStatistics ==========

    @Test
    void getBugStatistics_groupsCorrectly() {
        Bug b1 = new Bug();
        b1.setId(UUID.randomUUID());
        b1.setStatus("new");
        b1.setSeverity("fatal");
        b1.setPriority("high");
        b1.setReporterId("00000000-0000-0000-0000-000000000004");
        b1.setAssigneeId("00000000-0000-0000-0000-000000000005");

        Bug b2 = new Bug();
        b2.setId(UUID.randomUUID());
        b2.setStatus("new");
        b2.setSeverity("general");
        b2.setPriority("low");
        b2.setReporterId("00000000-0000-0000-0000-000000000004");

        when(bugMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(b1, b2));

        BugStatisticsRespDTO result = bugService.getBugStatistics(projectId);

        assertNotNull(result);
        assertEquals(2, result.getTotal());
        assertEquals(2L, result.getByStatus().get("new"));
        assertEquals(1L, result.getBySeverity().get("fatal"));
        assertEquals(1L, result.getBySeverity().get("general"));
        assertEquals(2L, result.getByReporter().get("00000000-0000-0000-0000-000000000004"));
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
        log.setOperatorId("00000000-0000-0000-0000-000000000004");
        log.setOperationType("create");
        log.setContent("Created");

        when(bugLogMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(log));

        SysUser operator = new SysUser();
        operator.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        operator.setUsername("operator");
        when(userMapper.selectById("00000000-0000-0000-0000-000000000004")).thenReturn(operator);

        List<BugLogRespDTO> result = bugService.getBugLogs(bugId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("operator", result.get(0).getOperatorName());
    }
}
