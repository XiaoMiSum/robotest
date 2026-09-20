package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugStatusChangeReqDTO;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.bug.BugLog;
import io.github.xiaomisum.robotest.repository.bug.BugLogMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import xyz.migoo.framework.common.exception.ServiceException;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BugStatusChangeServiceImplTest {

    @Mock
    private BugMapper bugMapper;
    @Mock
    private BugLogMapper bugLogMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Spy
    private BugWorkflow bugWorkflow = new BugWorkflowImpl();

    @InjectMocks
    private BugStatusChangeServiceImpl service;

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

    // ========== resolve ==========

    @Test
    void resolveBug_success() {
        UUID reporterId = UUID.fromString("00000000-0000-0000-0000-000000000004");
        Bug bug = activeBug();
        bug.setReporterId(reporterId);
        doAnswer(inv -> {
            ((BugLog) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(bugLogMapper).insert(any(BugLog.class));

        BugStatusChangeReqDTO reqDTO = statusChangeReq(Constants.BugStatus.RESOLVED, "修复完成");
        reqDTO.setResolution(Constants.BugResolution.FIXED);

        service.changeBugStatus(bug, userId, reqDTO);

        verify(bugMapper).resolveById(bugId, userId, Constants.BugResolution.FIXED, null, reporterId);
        verify(bugLogMapper).insert(any(BugLog.class));
    }

    @Test
    void resolveBug_withoutResolution_throws() {
        Bug bug = activeBug();

        BugStatusChangeReqDTO reqDTO = statusChangeReq(Constants.BugStatus.RESOLVED, null);

        assertThrows(ServiceException.class,
                () -> service.changeBugStatus(bug, userId, reqDTO));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt(), any());
    }

    @Test
    void resolveBug_invalidResolution_throws() {
        Bug bug = activeBug();

        BugStatusChangeReqDTO reqDTO = statusChangeReq(Constants.BugStatus.RESOLVED, null);
        reqDTO.setResolution("not_a_resolution");

        assertThrows(ServiceException.class,
                () -> service.changeBugStatus(bug, userId, reqDTO));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt(), any());
    }

    @Test
    void resolveBug_withoutComment_throws() {
        Bug bug = activeBug();

        BugStatusChangeReqDTO reqDTO = statusChangeReq(Constants.BugStatus.RESOLVED, null);
        reqDTO.setResolution(Constants.BugResolution.FIXED);

        assertThrows(ServiceException.class,
                () -> service.changeBugStatus(bug, userId, reqDTO));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt(), any());
    }

    @Test
    void resolveBug_duplicateWithoutOriginal_throws() {
        Bug bug = activeBug();

        BugStatusChangeReqDTO reqDTO = statusChangeReq(Constants.BugStatus.RESOLVED, "与已有缺陷重复");
        reqDTO.setResolution(Constants.BugResolution.DUPLICATE);

        assertThrows(ServiceException.class,
                () -> service.changeBugStatus(bug, userId, reqDTO));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt(), any());
    }

    @Test
    void resolveBug_duplicateOfSelf_throws() {
        Bug bug = activeBug();

        BugStatusChangeReqDTO reqDTO = statusChangeReq(Constants.BugStatus.RESOLVED, "与已有缺陷重复");
        reqDTO.setResolution(Constants.BugResolution.DUPLICATE);
        reqDTO.setDuplicateOfBugId(bugId);

        assertThrows(ServiceException.class,
                () -> service.changeBugStatus(bug, userId, reqDTO));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt(), any());
    }

    @Test
    void resolveBug_duplicateOfOtherProject_throws() {
        Bug bug = activeBug();
        UUID originalId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        Bug original = new Bug();
        original.setId(originalId);
        original.setProjectId(UUID.fromString("00000000-0000-0000-0000-000000000099"));

        when(bugMapper.selectById(originalId)).thenReturn(original);

        BugStatusChangeReqDTO reqDTO = statusChangeReq(Constants.BugStatus.RESOLVED, "与已有缺陷重复");
        reqDTO.setResolution(Constants.BugResolution.DUPLICATE);
        reqDTO.setDuplicateOfBugId(originalId);

        assertThrows(ServiceException.class,
                () -> service.changeBugStatus(bug, userId, reqDTO));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt(), any());
    }

    @Test
    void resolveBug_duplicateValid_success() {
        Bug bug = activeBug();
        UUID originalId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        Bug original = new Bug();
        original.setId(originalId);
        original.setProjectId(projectId);

        when(bugMapper.selectById(originalId)).thenReturn(original);
        doAnswer(inv -> {
            ((BugLog) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(bugLogMapper).insert(any(BugLog.class));

        BugStatusChangeReqDTO reqDTO = statusChangeReq(Constants.BugStatus.RESOLVED, "与已有缺陷重复");
        reqDTO.setResolution(Constants.BugResolution.DUPLICATE);
        reqDTO.setDuplicateOfBugId(originalId);

        service.changeBugStatus(bug, userId, reqDTO);

        verify(bugMapper).resolveById(bugId, userId, Constants.BugResolution.DUPLICATE, originalId, null);
    }

    // ========== close ==========

    @Test
    void closeBug_success() {
        Bug bug = activeBug();
        bug.setStatus(Constants.BugStatus.RESOLVED);
        doAnswer(inv -> {
            ((BugLog) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(bugLogMapper).insert(any(BugLog.class));

        service.changeBugStatus(bug, userId,
                statusChangeReq(Constants.BugStatus.CLOSED, "验证通过"));

        ArgumentCaptor<Bug> captor = ArgumentCaptor.forClass(Bug.class);
        verify(bugMapper).updateById(captor.capture());
        assertEquals(Constants.BugStatus.CLOSED, captor.getValue().getStatus());
        assertEquals(userId, captor.getValue().getClosedBy());
        assertNotNull(captor.getValue().getClosedAt());
        ArgumentCaptor<BugChangedEvent> eventCaptor = ArgumentCaptor.forClass(BugChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(BugChangeOp.CLOSED, eventCaptor.getValue().op());
        assertEquals(bugId, eventCaptor.getValue().bugId());
    }

    @Test
    void closeBug_withoutComment_throws() {
        Bug bug = activeBug();
        bug.setStatus(Constants.BugStatus.RESOLVED);

        assertThrows(ServiceException.class,
                () -> service.changeBugStatus(bug, userId,
                        statusChangeReq(Constants.BugStatus.CLOSED, null)));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt(), any());
    }

    // ========== reject ==========

    @Test
    void rejectBug_success_assigneeSetToReporter() {
        UUID reporterId = UUID.fromString("00000000-0000-0000-0000-000000000004");
        Bug bug = activeBug();
        bug.setReporterId(reporterId);
        doAnswer(inv -> {
            ((BugLog) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(bugLogMapper).insert(any(BugLog.class));

        service.changeBugStatus(bug, userId,
                statusChangeReq(Constants.BugStatus.REJECTED, "非缺陷，按设计工作"));

        ArgumentCaptor<Bug> captor = ArgumentCaptor.forClass(Bug.class);
        verify(bugMapper).updateById(captor.capture());
        assertEquals(Constants.BugStatus.REJECTED, captor.getValue().getStatus());
        assertEquals(reporterId, captor.getValue().getAssigneeId());
        assertEquals(userId, captor.getValue().getRejectedBy());
        verify(bugLogMapper).insert(any(BugLog.class));
    }

    @Test
    void rejectBug_withoutComment_throws() {
        Bug bug = activeBug();

        assertThrows(ServiceException.class,
                () -> service.changeBugStatus(bug, userId,
                        statusChangeReq(Constants.BugStatus.REJECTED, null)));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt(), any());
    }

    @Test
    void rejectBug_fromResolved_throws() {
        Bug bug = activeBug();
        bug.setStatus(Constants.BugStatus.RESOLVED);

        assertThrows(ServiceException.class,
                () -> service.changeBugStatus(bug, userId,
                        statusChangeReq(Constants.BugStatus.REJECTED, "拒绝已修复缺陷")));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt(), any());
    }

    @Test
    void rejectedBug_canBeClosed() {
        Bug bug = activeBug();
        bug.setStatus(Constants.BugStatus.REJECTED);
        doAnswer(inv -> {
            ((BugLog) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(bugLogMapper).insert(any(BugLog.class));

        service.changeBugStatus(bug, userId,
                statusChangeReq(Constants.BugStatus.CLOSED, "认可拒绝，关闭缺陷"));

        ArgumentCaptor<Bug> captor = ArgumentCaptor.forClass(Bug.class);
        verify(bugMapper).updateById(captor.capture());
        assertEquals(Constants.BugStatus.CLOSED, captor.getValue().getStatus());
    }

    @Test
    void rejectedBug_canBeReopened() {
        UUID rejecterId = UUID.fromString("00000000-0000-0000-0000-000000000006");
        Bug bug = activeBug();
        bug.setStatus(Constants.BugStatus.REJECTED);
        bug.setReopenCount(0);
        bug.setRejectedBy(rejecterId);
        doAnswer(inv -> {
            ((BugLog) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(bugLogMapper).insert(any(BugLog.class));

        service.changeBugStatus(bug, userId,
                statusChangeReq(Constants.BugStatus.ACTIVE, "不认可拒绝，重新激活"));

        verify(bugMapper).reopenById(bugId, 1, rejecterId);
    }

    @Test
    void rejectedBug_withoutRejectedBy_reopenKeepsAssignee() {
        Bug bug = activeBug();
        bug.setStatus(Constants.BugStatus.REJECTED);
        bug.setReopenCount(0);
        doAnswer(inv -> {
            ((BugLog) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(bugLogMapper).insert(any(BugLog.class));

        service.changeBugStatus(bug, userId,
                statusChangeReq(Constants.BugStatus.ACTIVE, "不认可拒绝，重新激活"));

        verify(bugMapper).reopenById(bugId, 1, null);
    }

    // ========== reopen ==========

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

        doAnswer(inv -> {
            ((BugLog) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(bugLogMapper).insert(any(BugLog.class));

        service.changeBugStatus(bug, userId,
                statusChangeReq(Constants.BugStatus.ACTIVE, "问题复现"));

        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper).reopenById(bugId, 2, userId);
    }

    @Test
    void resolvedBug_reopen_assigneeSetToResolver() {
        UUID resolverId = UUID.fromString("00000000-0000-0000-0000-000000000007");
        Bug bug = activeBug();
        bug.setStatus(Constants.BugStatus.RESOLVED);
        bug.setReopenCount(0);
        bug.setResolvedBy(resolverId);
        doAnswer(inv -> {
            ((BugLog) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(bugLogMapper).insert(any(BugLog.class));

        service.changeBugStatus(bug, userId,
                statusChangeReq(Constants.BugStatus.ACTIVE, "修复未通过验证，重新激活"));

        verify(bugMapper).reopenById(bugId, 1, resolverId);
    }

    @Test
    void reopenBug_withoutComment_throws() {
        Bug bug = activeBug();
        bug.setStatus(Constants.BugStatus.RESOLVED);

        assertThrows(ServiceException.class,
                () -> service.changeBugStatus(bug, userId,
                        statusChangeReq(Constants.BugStatus.ACTIVE, null)));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt(), any());
    }

    // ========== 非法流转 ==========

    @Test
    void changeBugStatus_activeToClosed_throws() {
        Bug bug = activeBug();

        assertThrows(ServiceException.class,
                () -> service.changeBugStatus(bug, userId,
                        statusChangeReq(Constants.BugStatus.CLOSED, "跳过解决直接关闭")));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt(), any());
    }

    @Test
    void changeBugStatus_closedToResolved_throws() {
        Bug bug = activeBug();
        bug.setStatus(Constants.BugStatus.CLOSED);

        BugStatusChangeReqDTO reqDTO = statusChangeReq(Constants.BugStatus.RESOLVED, null);
        reqDTO.setResolution(Constants.BugResolution.FIXED);

        assertThrows(ServiceException.class,
                () -> service.changeBugStatus(bug, userId, reqDTO));
        verify(bugMapper, never()).updateById(any(Bug.class));
        verify(bugMapper, never()).resolveById(any(), any(), any(), any(), any());
        verify(bugMapper, never()).reopenById(any(), anyInt(), any());
    }
}
