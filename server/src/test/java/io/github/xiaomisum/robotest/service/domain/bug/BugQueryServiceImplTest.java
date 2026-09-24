package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.convert.BugConvertMapper;
import io.github.xiaomisum.robotest.model.convert.BugConvertMapperImpl;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugLogRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.bug.BugLog;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.bug.BugLogMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.pojo.PageResult;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BugQueryServiceImplTest {

    @Mock
    private BugMapper bugMapper;
    @Mock
    private BugLogMapper bugLogMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ProjectModuleMapper projectModuleMapper;
    @Mock
    private ProjectAccessGuard projectAccessGuard;
    @Spy
    private BugConvertMapper bugConvertMapper = new BugConvertMapperImpl();

    @InjectMocks
    private BugQueryServiceImpl bugQueryService;

    private UUID projectId;
    private UUID userId;
    private UUID bugId;

    @BeforeEach
    void setUp() {
        projectId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        userId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        bugId = UUID.fromString("00000000-0000-0000-0000-000000000003");
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
        when(userMapper.listByIds(anyCollection())).thenReturn(List.of(reporter));

        PageResult<BugListRespDTO> result = bugQueryService.getBugPage(
                projectId, userId, Constants.BugStatus.ACTIVE, "high", "high",
                Constants.BugType.CODE_ERROR, null, null, null, null, null, 1, 10);

        assertNotNull(result);
        assertEquals(1, result.getList().size());
        assertEquals(1L, result.getTotal());
        assertEquals("Test Bug", result.getList().get(0).getTitle());
        assertEquals(Constants.BugType.CODE_ERROR, result.getList().get(0).getBugType());
        assertEquals("reporter", result.getList().get(0).getReporter().getName());
        verify(projectAccessGuard).requireProjectMember(projectId, userId);
    }

    @Test
    void getBugPage_withResolvedInfo() {
        UUID reporterId = UUID.fromString("00000000-0000-0000-0000-000000000004");
        UUID resolverId = UUID.fromString("00000000-0000-0000-0000-000000000005");
        LocalDateTime resolvedAt = LocalDateTime.of(2026, 7, 30, 10, 0);
        LocalDateTime closedAt = LocalDateTime.of(2026, 7, 30, 12, 0);

        Bug bug = new Bug();
        bug.setId(bugId);
        bug.setTitle("Resolved Bug");
        bug.setStatus(Constants.BugStatus.CLOSED);
        bug.setResolution(Constants.BugResolution.FIXED);
        bug.setReporterId(reporterId);
        bug.setResolvedBy(resolverId);
        bug.setResolvedAt(resolvedAt);
        bug.setClosedAt(closedAt);

        PageResult<Bug> pageResult = new PageResult<>(List.of(bug), 1L);
        doReturn(pageResult).when(bugMapper).findPage(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        SysUser reporter = new SysUser();
        reporter.setId(reporterId);
        reporter.setUsername("reporter");
        SysUser resolver = new SysUser();
        resolver.setId(resolverId);
        resolver.setUsername("resolver");
        when(userMapper.listByIds(anyCollection())).thenReturn(List.of(reporter, resolver));

        PageResult<BugListRespDTO> result = bugQueryService.getBugPage(
                projectId, userId, null, null, null, null, null, null, null, null, null, 1, 10);

        BugListRespDTO dto = result.getList().get(0);
        assertEquals("resolver", dto.getResolvedBy().getName());
        assertEquals(Constants.BugResolution.FIXED, dto.getResolution());
        assertEquals(resolvedAt, dto.getResolvedAt());
        assertEquals(closedAt, dto.getClosedAt());
    }

    @Test
    void getBugPage_emptyResult() {
        PageResult<Bug> pageResult = new PageResult<>(Collections.emptyList(), 0L);
        doReturn(pageResult).when(bugMapper).findPage(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        PageResult<BugListRespDTO> result = bugQueryService.getBugPage(
                projectId, userId, null, null, null, null, null, null, null, null, null, 1, 10);

        assertNotNull(result);
        assertTrue(result.getList().isEmpty());
        assertEquals(0L, result.getTotal());
        verify(projectAccessGuard).requireProjectMember(projectId, userId);
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

        BugDetailRespDTO result = bugQueryService.getBugDetail(bugId, userId);

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
                () -> bugQueryService.getBugDetail(bugId, userId));
    }

    // ========== getBugLogs ==========

    @Test
    void getBugLogs_success() {
        Bug bug = activeBug();
        when(bugMapper.selectById(bugId)).thenReturn(bug);

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

        List<BugLogRespDTO> result = bugQueryService.getBugLogs(bugId, userId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("operator", result.get(0).getOperatorName());
        verify(projectAccessGuard).requireProjectMember(projectId, userId);
    }

    @Test
    void getBugLogs_bugNotFound_throws() {
        when(bugMapper.selectById(bugId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> bugQueryService.getBugLogs(bugId, userId));
        verify(projectAccessGuard, never()).requireProjectMember(any(), any());
    }
}