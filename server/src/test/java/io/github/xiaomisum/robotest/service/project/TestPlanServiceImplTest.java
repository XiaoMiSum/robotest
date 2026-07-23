package io.github.xiaomisum.robotest.service.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.model.dto.request.TestPlanCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.TestPlanRecordReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.TestPlanDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.TestPlanExecutionRecordRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.TestPlanListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.TestPlanSnapshotNodeRespDTO;
import io.github.xiaomisum.robotest.model.entity.*;
import io.github.xiaomisum.robotest.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.response.TestPlanProgressRespDTO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestPlanServiceImplTest {

    @Mock
    private TestPlanMapper testPlanMapper;
    @Mock
    private TestPlanModuleSnapshotMapper planModuleSnapshotMapper;
    @Mock
    private TestPlanNodeSnapshotMapper planNodeSnapshotMapper;
    @Mock
    private TestPlanExecutionRecordMapper planExecutionRecordMapper;
    @Mock
    private TestCaseModuleMapper testCaseModuleMapper;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private SysUserMapper userMapper;

    @InjectMocks
    private TestPlanServiceImpl planService;

    private String projectId;
    private String userId;
    private String planId;

    @BeforeEach
    void setUp() {
        projectId = "00000000-0000-0000-0000-000000000001";
        userId = "00000000-0000-0000-0000-000000000002";
        planId = "00000000-0000-0000-0000-000000000003";
    }

    @Test
    void getPlanPage_success() {
        TestPlan plan = new TestPlan();
        plan.setId(UUID.fromString(planId));
        plan.setName("Plan 1");
        plan.setStatus("new");
        plan.setExecutorId(userId);

        PageResult<TestPlan> page = new PageResult<>(List.of(plan), 1L);
        doReturn(page).when(testPlanMapper).selectPage(any(PageParam.class), any(LambdaQueryWrapper.class));

        SysUser executor = new SysUser();
        executor.setId(UUID.fromString(userId));
        executor.setUsername("executor");
        when(userMapper.selectById(userId)).thenReturn(executor);

        PageResult<TestPlanListRespDTO> result = planService.getPlanPage(
                projectId, null, 1, 10);

        assertNotNull(result);
        assertEquals(1, result.getList().size());
        assertEquals("Plan 1", result.getList().get(0).getName());
        assertEquals("executor", result.getList().get(0).getExecutor().getName());
    }

    @Test
    void getPlanPage_empty() {
        PageResult<TestPlan> page = new PageResult<>(Collections.emptyList(), 0L);
        doReturn(page).when(testPlanMapper).selectPage(any(PageParam.class), any(LambdaQueryWrapper.class));

        PageResult<TestPlanListRespDTO> result = planService.getPlanPage(
                projectId, null, 1, 10);

        assertNotNull(result);
        assertTrue(result.getList().isEmpty());
    }

    @Test
    void createPlan_success() {
        doAnswer(inv -> {
            ((TestPlan) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(testPlanMapper).insert(any(TestPlan.class));

        TestPlanCreateReqDTO reqDTO = new TestPlanCreateReqDTO();
        reqDTO.setName("New Plan");
        reqDTO.setSelectedNodes(Collections.emptyList());

        TestPlanDetailRespDTO result = planService.createPlan(projectId, userId, reqDTO);

        assertNotNull(result);
        assertEquals("New Plan", result.getName());
        verify(testPlanMapper).insert(any(TestPlan.class));
    }

    @Test
    void getPlanDetail_success() {
        TestPlan plan = new TestPlan();
        plan.setId(UUID.fromString(planId));
        plan.setName("Plan");
        plan.setExecutorId(userId);

        when(testPlanMapper.selectById(planId)).thenReturn(plan);
        when(userMapper.selectById(userId)).thenReturn(null);

        TestPlanDetailRespDTO result = planService.getPlanDetail(planId);

        assertNotNull(result);
        assertEquals("Plan", result.getName());
    }

    @Test
    void getPlanDetail_notFound_throws() {
        when(testPlanMapper.selectById(planId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> planService.getPlanDetail(planId));
    }

    @Test
    void getPlanSnapshotTree_success() {
        TestPlan plan = new TestPlan();
        plan.setId(UUID.fromString(planId));

        when(testPlanMapper.selectById(planId)).thenReturn(plan);

        TestPlanNodeSnapshot snapshot = new TestPlanNodeSnapshot();
        snapshot.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        snapshot.setPlanId(planId);
        snapshot.setIsAssociated(true);
        snapshot.setParentId(null);

        when(planNodeSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(snapshot));

        List<TestPlanSnapshotNodeRespDTO> result =
                planService.getPlanSnapshotTree(planId, null);

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void getPlanSnapshotTree_notFound_throws() {
        when(testPlanMapper.selectById(planId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> planService.getPlanSnapshotTree(planId, null));
    }

    @Test
    void submitExecutionRecord_success() {
        TestPlan plan = new TestPlan();
        plan.setId(UUID.fromString(planId));
        plan.setStatus("in_progress");

        when(testPlanMapper.selectById(planId)).thenReturn(plan);

        TestPlanNodeSnapshot snapshot = new TestPlanNodeSnapshot();
        snapshot.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        snapshot.setPlanId(planId);
        snapshot.setIsAssociated(true);
        snapshot.setType("case");

        when(planNodeSnapshotMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000004"))).thenReturn(snapshot);

        TestPlanRecordReqDTO reqDTO = new TestPlanRecordReqDTO();
        reqDTO.setSnapshotNodeId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        reqDTO.setResult("pass");
        reqDTO.setNote("Looks good");

        planService.submitExecutionRecord(planId, userId, reqDTO);

        verify(planNodeSnapshotMapper).updateById(any(TestPlanNodeSnapshot.class));
        verify(planExecutionRecordMapper).insert(any(TestPlanExecutionRecord.class));
        assertEquals("pass", snapshot.getLastResult());
        assertEquals(userId, snapshot.getLastExecutorId());
    }

    @Test
    void submitExecutionRecord_planNotFound_throws() {
        when(testPlanMapper.selectById(planId)).thenReturn(null);

        TestPlanRecordReqDTO reqDTO = new TestPlanRecordReqDTO();
        reqDTO.setSnapshotNodeId(UUID.fromString("00000000-0000-0000-0000-000000000099"));
        reqDTO.setResult("pass");

        assertThrows(ServiceException.class,
                () -> planService.submitExecutionRecord(planId, userId, reqDTO));
    }

    @Test
    void submitExecutionRecord_notInProgress_throws() {
        TestPlan plan = new TestPlan();
        plan.setId(UUID.fromString(planId));
        plan.setStatus("completed");

        when(testPlanMapper.selectById(planId)).thenReturn(plan);

        TestPlanRecordReqDTO reqDTO = new TestPlanRecordReqDTO();
        reqDTO.setSnapshotNodeId(UUID.fromString("00000000-0000-0000-0000-000000000099"));
        reqDTO.setResult("pass");

        assertThrows(ServiceException.class,
                () -> planService.submitExecutionRecord(planId, userId, reqDTO));
    }

    @Test
    void submitExecutionRecord_snapshotNotFound_throws() {
        TestPlan plan = new TestPlan();
        plan.setId(UUID.fromString(planId));
        plan.setStatus("in_progress");

        when(testPlanMapper.selectById(planId)).thenReturn(plan);
        when(planNodeSnapshotMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000004"))).thenReturn(null);

        TestPlanRecordReqDTO reqDTO = new TestPlanRecordReqDTO();
        reqDTO.setSnapshotNodeId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        reqDTO.setResult("pass");

        assertThrows(ServiceException.class,
                () -> planService.submitExecutionRecord(planId, userId, reqDTO));
    }

    @Test
    void submitExecutionRecord_notAssociated_throws() {
        TestPlan plan = new TestPlan();
        plan.setId(UUID.fromString(planId));
        plan.setStatus("in_progress");

        when(testPlanMapper.selectById(planId)).thenReturn(plan);

        TestPlanNodeSnapshot snapshot = new TestPlanNodeSnapshot();
        snapshot.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        snapshot.setPlanId(planId);
        snapshot.setIsAssociated(false);
        snapshot.setType("case");

        when(planNodeSnapshotMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000004"))).thenReturn(snapshot);

        TestPlanRecordReqDTO reqDTO = new TestPlanRecordReqDTO();
        reqDTO.setSnapshotNodeId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        reqDTO.setResult("pass");

        assertThrows(ServiceException.class,
                () -> planService.submitExecutionRecord(planId, userId, reqDTO));
    }

    @Test
    void submitExecutionRecord_notCaseType_throws() {
        TestPlan plan = new TestPlan();
        plan.setId(UUID.fromString(planId));
        plan.setStatus("in_progress");

        when(testPlanMapper.selectById(planId)).thenReturn(plan);

        TestPlanNodeSnapshot snapshot = new TestPlanNodeSnapshot();
        snapshot.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        snapshot.setPlanId(planId);
        snapshot.setIsAssociated(true);
        snapshot.setType("normal");

        when(planNodeSnapshotMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000004"))).thenReturn(snapshot);

        TestPlanRecordReqDTO reqDTO = new TestPlanRecordReqDTO();
        reqDTO.setSnapshotNodeId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        reqDTO.setResult("pass");

        assertThrows(ServiceException.class,
                () -> planService.submitExecutionRecord(planId, userId, reqDTO));
    }

    @Test
    void getNodeExecutionRecords_success() {
        TestPlanExecutionRecord record = new TestPlanExecutionRecord();
        record.setId(UUID.fromString("00000000-0000-0000-0000-000000000005"));
        record.setPlanId(planId);
        record.setSnapshotNodeId("00000000-0000-0000-0000-000000000004");
        record.setExecutorId(userId);
        record.setResult("pass");

        when(planExecutionRecordMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(record));
        when(userMapper.selectById(userId)).thenReturn(null);

        List<TestPlanExecutionRecordRespDTO> result =
                planService.getNodeExecutionRecords(planId, "00000000-0000-0000-0000-000000000004");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("pass", result.get(0).getResult());
    }

    @Test
    void syncPlan_success() {
        TestPlan plan = new TestPlan();
        plan.setId(UUID.fromString(planId));
        plan.setExecutorId(userId);
        plan.setStatus("in_progress");

        when(testPlanMapper.selectById(planId)).thenReturn(plan);

        TestPlanNodeSnapshot snapshot = new TestPlanNodeSnapshot();
        snapshot.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        snapshot.setPlanId(planId);
        snapshot.setOriginalNodeId("00000000-0000-0000-0000-000000000006");
        snapshot.setTitle("Old Title");
        snapshot.setType("normal");

        when(planNodeSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(snapshot));

        TestCaseNode currentNode = new TestCaseNode();
        currentNode.setId(UUID.fromString("00000000-0000-0000-0000-000000000006"));
        currentNode.setTitle("Updated Title");
        currentNode.setType("case");
        currentNode.setPriority("high");
        currentNode.setSortOrder(0);
        currentNode.setIsDeleted(false);

        when(testCaseNodeMapper.selectById("00000000-0000-0000-0000-000000000006")).thenReturn(currentNode);

        planService.syncPlan(planId, userId);

        verify(planNodeSnapshotMapper).updateById(any(TestPlanNodeSnapshot.class));
        assertEquals("Updated Title", snapshot.getTitle());
        assertEquals("case", snapshot.getType());
    }

    @Test
    void syncPlan_notExecutor_throws() {
        TestPlan plan = new TestPlan();
        plan.setId(UUID.fromString(planId));
        plan.setExecutorId("other-user");
        plan.setStatus("in_progress");

        when(testPlanMapper.selectById(planId)).thenReturn(plan);

        assertThrows(ServiceException.class,
                () -> planService.syncPlan(planId, userId));
    }

    @Test
    void syncPlan_notInProgress_throws() {
        TestPlan plan = new TestPlan();
        plan.setId(UUID.fromString(planId));
        plan.setExecutorId(userId);
        plan.setStatus("completed");

        when(testPlanMapper.selectById(planId)).thenReturn(plan);

        assertThrows(ServiceException.class,
                () -> planService.syncPlan(planId, userId));
    }

    @Test
    void syncPlan_deletedOriginal_marksDeleted() {
        TestPlan plan = new TestPlan();
        plan.setId(UUID.fromString(planId));
        plan.setExecutorId(userId);
        plan.setStatus("in_progress");

        when(testPlanMapper.selectById(planId)).thenReturn(plan);

        TestPlanNodeSnapshot snapshot = new TestPlanNodeSnapshot();
        snapshot.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        snapshot.setPlanId(planId);
        snapshot.setOriginalNodeId("00000000-0000-0000-0000-000000000006");

        when(planNodeSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(snapshot));
        when(testCaseNodeMapper.selectById("00000000-0000-0000-0000-000000000006")).thenReturn(null);

        planService.syncPlan(planId, userId);

        verify(planNodeSnapshotMapper).updateById(any(TestPlanNodeSnapshot.class));
        assertTrue(snapshot.getIsDeleted());
    }

    @Test
    void closePlan_success() {
        TestPlan plan = new TestPlan();
        plan.setId(UUID.fromString(planId));
        plan.setExecutorId(userId);
        plan.setStatus("in_progress");

        when(testPlanMapper.selectById(planId)).thenReturn(plan);
        when(planNodeSnapshotMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(0L);

        planService.closePlan(planId, userId);

        verify(testPlanMapper).updateById(any(TestPlan.class));
        assertEquals("closed", plan.getStatus());
    }

    @Test
    void closePlan_withUntestedCases_warns() {
        TestPlan plan = new TestPlan();
        plan.setId(UUID.fromString(planId));
        plan.setExecutorId(userId);
        plan.setStatus("in_progress");

        when(testPlanMapper.selectById(planId)).thenReturn(plan);
        when(planNodeSnapshotMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(3L);

        planService.closePlan(planId, userId);

        verify(testPlanMapper).updateById(any(TestPlan.class));
        assertEquals("closed", plan.getStatus());
    }

    @Test
    void closePlan_notExecutor_throws() {
        TestPlan plan = new TestPlan();
        plan.setId(UUID.fromString(planId));
        plan.setExecutorId("other-user");

        when(testPlanMapper.selectById(planId)).thenReturn(plan);

        assertThrows(ServiceException.class,
                () -> planService.closePlan(planId, userId));
    }

    @Test
    void closePlan_notFound_throws() {
        when(testPlanMapper.selectById(planId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> planService.closePlan(planId, userId));
    }

    // ========== startPlan ==========

    @Test
    void startPlan_success() {
        TestPlan plan = new TestPlan();
        plan.setId(UUID.fromString(planId));
        plan.setExecutorId(userId);
        plan.setStatus(Constants.Status.NEW);

        when(testPlanMapper.selectById(planId)).thenReturn(plan);

        planService.startPlan(planId, userId);

        assertEquals(Constants.Status.IN_PROGRESS, plan.getStatus());
        verify(testPlanMapper).updateById(plan);
    }

    @Test
    void startPlan_notFound_throws() {
        when(testPlanMapper.selectById(planId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> planService.startPlan(planId, userId));
    }

    @Test
    void startPlan_notExecutor_throws() {
        TestPlan plan = new TestPlan();
        plan.setId(UUID.fromString(planId));
        plan.setExecutorId("other-user");
        plan.setStatus(Constants.Status.NEW);

        when(testPlanMapper.selectById(planId)).thenReturn(plan);

        assertThrows(ServiceException.class,
                () -> planService.startPlan(planId, userId));
    }

    @Test
    void startPlan_notNewStatus_throws() {
        TestPlan plan = new TestPlan();
        plan.setId(UUID.fromString(planId));
        plan.setExecutorId(userId);
        plan.setStatus(Constants.Status.IN_PROGRESS);

        when(testPlanMapper.selectById(planId)).thenReturn(plan);

        assertThrows(ServiceException.class,
                () -> planService.startPlan(planId, userId));
    }

    // ========== getPlanProgress ==========

    @Test
    void getPlanProgress_success() {
        TestPlan plan = new TestPlan();
        plan.setId(UUID.fromString(planId));
        when(testPlanMapper.selectById(planId)).thenReturn(plan);

        TestPlanNodeSnapshot snap1 = new TestPlanNodeSnapshot();
        snap1.setLastResult("pass");
        TestPlanNodeSnapshot snap2 = new TestPlanNodeSnapshot();
        snap2.setLastResult("fail");
        TestPlanNodeSnapshot snap3 = new TestPlanNodeSnapshot();
        snap3.setLastResult(null);

        when(planNodeSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(snap1, snap2, snap3));

        TestPlanProgressRespDTO result = planService.getPlanProgress(planId);

        assertEquals(3, result.getTotalAssociated());
        assertEquals(1, result.getPassed());
        assertEquals(1, result.getFailed());
        assertEquals(1, result.getUntested());
    }

    @Test
    void getPlanProgress_notFound_throws() {
        when(testPlanMapper.selectById(planId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> planService.getPlanProgress(planId));
    }

    @Test
    void getPlanProgress_emptySnapshots() {
        TestPlan plan = new TestPlan();
        plan.setId(UUID.fromString(planId));
        when(testPlanMapper.selectById(planId)).thenReturn(plan);
        when(planNodeSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(new ArrayList<>());

        TestPlanProgressRespDTO result = planService.getPlanProgress(planId);

        assertEquals(0, result.getTotalAssociated());
        assertEquals(0.0, result.getProgressPercent());
    }

    // ========== syncPlan module snapshot ==========

    @Test
    void syncPlan_syncsModuleName() {
        TestPlan plan = new TestPlan();
        plan.setId(UUID.fromString(planId));
        plan.setExecutorId(userId);
        plan.setStatus("in_progress");

        when(testPlanMapper.selectById(planId)).thenReturn(plan);

        UUID moduleSnapId = UUID.randomUUID();
        TestPlanModuleSnapshot moduleSnap = new TestPlanModuleSnapshot();
        moduleSnap.setId(moduleSnapId);
        moduleSnap.setOriginalModuleId("00000000-0000-0000-0000-000000000010");
        moduleSnap.setName("old name");
        moduleSnap.setSortOrder(1);

        when(planModuleSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(moduleSnap));
        when(planNodeSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(new ArrayList<>());

        TestCaseModule originalModule = new TestCaseModule();
        originalModule.setName("new name");
        originalModule.setSortOrder(2);
        originalModule.setIsDeleted(false);
        when(testCaseModuleMapper.selectById("00000000-0000-0000-0000-000000000010"))
                .thenReturn(originalModule);

        planService.syncPlan(planId, userId);

        assertEquals("new name", moduleSnap.getName());
        assertEquals(2, moduleSnap.getSortOrder());
        verify(planModuleSnapshotMapper).updateById(moduleSnap);
    }

    @Test
    void syncPlan_deletesRemovedModule() {
        TestPlan plan = new TestPlan();
        plan.setId(UUID.fromString(planId));
        plan.setExecutorId(userId);
        plan.setStatus("in_progress");

        when(testPlanMapper.selectById(planId)).thenReturn(plan);

        UUID moduleSnapId = UUID.randomUUID();
        TestPlanModuleSnapshot moduleSnap = new TestPlanModuleSnapshot();
        moduleSnap.setId(moduleSnapId);
        moduleSnap.setOriginalModuleId("00000000-0000-0000-0000-000000000010");

        when(planModuleSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(moduleSnap));
        when(planNodeSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(new ArrayList<>());
        when(testCaseModuleMapper.selectById("00000000-0000-0000-0000-000000000010"))
                .thenReturn(null);

        planService.syncPlan(planId, userId);

        verify(planModuleSnapshotMapper).deleteById(moduleSnapId);
    }

    @Test
    void syncPlan_deletedModule_cascadesNodeDeletion() {
        TestPlan plan = new TestPlan();
        plan.setId(UUID.fromString(planId));
        plan.setExecutorId(userId);
        plan.setStatus("in_progress");

        when(testPlanMapper.selectById(planId)).thenReturn(plan);

        UUID moduleSnapId = UUID.randomUUID();
        TestPlanModuleSnapshot moduleSnap = new TestPlanModuleSnapshot();
        moduleSnap.setId(moduleSnapId);
        moduleSnap.setOriginalModuleId("00000000-0000-0000-0000-000000000010");

        UUID nodeSnapId = UUID.randomUUID();
        TestPlanNodeSnapshot nodeSnap = new TestPlanNodeSnapshot();
        nodeSnap.setId(nodeSnapId);
        nodeSnap.setDocumentSnapshotId(moduleSnapId.toString());

        when(planModuleSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(moduleSnap));
        when(planNodeSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(nodeSnap));
        when(testCaseModuleMapper.selectById("00000000-0000-0000-0000-000000000010"))
                .thenReturn(null);

        planService.syncPlan(planId, userId);

        verify(planModuleSnapshotMapper).deleteById(moduleSnapId);
        verify(planNodeSnapshotMapper).deleteById(nodeSnapId);
    }
}
