package io.github.xiaomisum.robotest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.BugCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.BugUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugLogRespDTO;
import io.github.xiaomisum.robotest.model.entity.Bug;
import io.github.xiaomisum.robotest.model.entity.BugLog;
import io.github.xiaomisum.robotest.model.entity.SysUser;
import io.github.xiaomisum.robotest.repository.BugLogMapper;
import io.github.xiaomisum.robotest.repository.BugMapper;
import io.github.xiaomisum.robotest.repository.SysUserMapper;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BugServiceImplTest {

    @Mock
    private BugMapper bugMapper;
    @Mock
    private BugLogMapper bugLogMapper;
    @Mock
    private SysUserMapper userMapper;

    @InjectMocks
    private BugServiceImpl bugService;

    private String projectId;
    private String userId;
    private String bugId;

    @BeforeEach
    void setUp() {
        projectId = "proj-1";
        userId = "user-1";
        bugId = "bug-1";
    }

    @Test
    void getBugPage_withFilters() {
        Bug bug = new Bug();
        bug.setId(bugId);
        bug.setTitle("Test Bug");
        bug.setSeverity("high");
        bug.setPriority("high");
        bug.setStatus("new");
        bug.setReporterId("user-1");

        PageResult<Bug> pageResult = new PageResult<>(List.of(bug), 1L);
        doReturn(pageResult).when(bugMapper).selectPage(any(PageParam.class), any(LambdaQueryWrapper.class));

        SysUser reporter = new SysUser();
        reporter.setId("user-1");
        reporter.setUsername("reporter");
        when(userMapper.selectById("user-1")).thenReturn(reporter);

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

    @Test
    void createBug_success() {
        BugCreateReqDTO reqDTO = new BugCreateReqDTO();
        reqDTO.setTitle("New Bug");
        reqDTO.setSeverity("high");
        reqDTO.setPriority("high");
        reqDTO.setDescription("desc");

        String result = bugService.createBug(projectId, userId, reqDTO);

        assertNotNull(result);
        verify(bugMapper).insert(any(Bug.class));
        verify(bugLogMapper).insert(any(BugLog.class));
    }

    @Test
    void updateBug_success() {
        Bug bug = new Bug();
        bug.setId(bugId);
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

    @Test
    void getBugLogs_returnsLogs() {
        BugLog log = new BugLog();
        log.setId("log-1");
        log.setBugId(bugId);
        log.setOperatorId("user-1");
        log.setOperationType("create");
        log.setContent("Created");

        when(bugLogMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(log));

        SysUser operator = new SysUser();
        operator.setId("user-1");
        operator.setUsername("operator");
        when(userMapper.selectById("user-1")).thenReturn(operator);

        List<BugLogRespDTO> result = bugService.getBugLogs(bugId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("operator", result.get(0).getOperatorName());
    }
}
