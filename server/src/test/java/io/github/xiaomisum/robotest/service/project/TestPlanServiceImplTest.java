package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.plan.TestPlanCasesUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.plan.TestPlanCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.plan.TestPlanRecordReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.PlannedCasesRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanExecutionRecordRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanSnapshotNodeRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlan;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlanExecutionRecord;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlanModuleSnapshot;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlanNodeSnapshot;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.repository.plan.TestPlanMapper;
import io.github.xiaomisum.robotest.repository.plan.TestPlanModuleSnapshotMapper;
import io.github.xiaomisum.robotest.repository.plan.TestPlanNodeSnapshotMapper;
import io.github.xiaomisum.robotest.repository.plan.TestPlanExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanProgressRespDTO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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
        @Mock
        private ProjectAccessGuard projectAccessGuard;

        @InjectMocks
        private TestPlanServiceImpl planService;

        private UUID projectId;
        private UUID userId;
        private UUID planId;
        private UUID otherUserId;

        @BeforeEach
        void setUp() {
                projectId = UUID.fromString("00000000-0000-0000-0000-000000000001");
                userId = UUID.fromString("00000000-0000-0000-0000-000000000002");
                planId = UUID.fromString("00000000-0000-0000-0000-000000000003");
                otherUserId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        }

        @Test
        void getPlanPage_success() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setName("Plan 1");
                plan.setStatus("new");
                plan.setExecutorId(userId);

                PageResult<TestPlan> page = new PageResult<>(List.of(plan), 1L);
                doReturn(page).when(testPlanMapper).findPage(any(PageParam.class), eq(projectId), isNull(), isNull());

                SysUser executor = new SysUser();
                executor.setId(userId);
                executor.setUsername("executor");
                when(userMapper.selectById(userId)).thenReturn(executor);

                PageResult<TestPlanListRespDTO> result = planService.getPlanPage(
                                projectId, userId, null, null, 1, 10);

                assertNotNull(result);
                assertEquals(1, result.getList().size());
                assertEquals("Plan 1", result.getList().get(0).getName());
                assertEquals("executor", result.getList().get(0).getExecutor().getName());
                verify(projectAccessGuard).requireProjectMember(projectId, userId);
        }

        @Test
        void getPlanPage_empty() {
                PageResult<TestPlan> page = new PageResult<>(Collections.emptyList(), 0L);
                doReturn(page).when(testPlanMapper).findPage(any(PageParam.class), eq(projectId), isNull(), isNull());

                PageResult<TestPlanListRespDTO> result = planService.getPlanPage(
                                projectId, userId, null, null, 1, 10);

                assertNotNull(result);
                assertTrue(result.getList().isEmpty());
                verify(projectAccessGuard).requireProjectMember(projectId, userId);
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
                ArgumentCaptor<TestPlan> planCaptor = ArgumentCaptor.forClass(TestPlan.class);
                verify(testPlanMapper).insert(planCaptor.capture());
                assertNotNull(planCaptor.getValue().getSnapshotSyncedAt());
        }

        @Test
        void getPlanDetail_success() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
                plan.setName("Plan");
                plan.setExecutorId(userId);

                when(testPlanMapper.selectById(planId)).thenReturn(plan);
                when(userMapper.selectById(userId)).thenReturn(null);

                TestPlanDetailRespDTO result = planService.getPlanDetail(planId, userId);

                assertNotNull(result);
                assertEquals("Plan", result.getName());
                verify(projectAccessGuard).requireProjectMember(projectId, userId);
        }

        @Test
        void getPlanDetail_notFound_throws() {
                when(testPlanMapper.selectById(planId)).thenReturn(null);

                assertThrows(ServiceException.class,
                                () -> planService.getPlanDetail(planId, userId));
                verify(projectAccessGuard, never()).requireProjectMember(any(), any());
        }

        @Test
        void getPlanSnapshotTree_success() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                TestPlanNodeSnapshot snapshot = new TestPlanNodeSnapshot();
                snapshot.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
                snapshot.setPlanId(planId);
                snapshot.setIsAssociated(true);
                snapshot.setParentId(null);

                when(planNodeSnapshotMapper.listByPlanIdAndDocumentId(planId, null))
                                .thenReturn(List.of(snapshot));

                List<TestPlanSnapshotNodeRespDTO> result = planService.getPlanSnapshotTree(planId, null, userId);

                assertNotNull(result);
                assertFalse(result.isEmpty());
                verify(projectAccessGuard).requireProjectMember(projectId, userId);
        }

        @Test
        void getPlanSnapshotTree_notFound_throws() {
                when(testPlanMapper.selectById(planId)).thenReturn(null);

                assertThrows(ServiceException.class,
                                () -> planService.getPlanSnapshotTree(planId, null, userId));
                verify(projectAccessGuard, never()).requireProjectMember(any(), any());
        }

        @Test
        void submitExecutionRecord_success() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setStatus("in_progress");

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                TestPlanNodeSnapshot snapshot = new TestPlanNodeSnapshot();
                snapshot.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
                snapshot.setPlanId(planId);
                snapshot.setIsAssociated(true);
                snapshot.setType("case");

                when(planNodeSnapshotMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000004")))
                                .thenReturn(snapshot);

                TestPlanRecordReqDTO reqDTO = new TestPlanRecordReqDTO();
                reqDTO.setSnapshotNodeId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
                reqDTO.setResult("pass");
                reqDTO.setNote("Looks good");

                planService.submitExecutionRecord(planId, userId, reqDTO);

                // 更新载体仅携带 id + 本次标记字段，不再回写查询实体
                ArgumentCaptor<TestPlanNodeSnapshot> captor = ArgumentCaptor.forClass(TestPlanNodeSnapshot.class);
                verify(planNodeSnapshotMapper).updateById(captor.capture());
                verify(planExecutionRecordMapper).insert(any(TestPlanExecutionRecord.class));
                assertEquals("pass", captor.getValue().getLastResult());
                assertEquals(userId, captor.getValue().getLastExecutorId());
        }

        @Test
        void submitExecutionRecord_newPlan_setsInProgress() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setStatus(Constants.Status.NEW);

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                TestPlanNodeSnapshot snapshot = new TestPlanNodeSnapshot();
                snapshot.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
                snapshot.setPlanId(planId);
                snapshot.setIsAssociated(true);
                snapshot.setType("case");

                when(planNodeSnapshotMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000004")))
                                .thenReturn(snapshot);

                TestPlanRecordReqDTO reqDTO = new TestPlanRecordReqDTO();
                reqDTO.setSnapshotNodeId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
                reqDTO.setResult("pass");

                planService.submitExecutionRecord(planId, userId, reqDTO);

                ArgumentCaptor<TestPlan> captor = ArgumentCaptor.forClass(TestPlan.class);
                verify(testPlanMapper).updateById(captor.capture());
                assertEquals(Constants.Status.IN_PROGRESS, captor.getValue().getStatus());
        }

        @Test
        void updatePlanCases_finished_throws() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
                plan.setStatus("closed");
                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                TestPlanCasesUpdateReqDTO reqDTO = new TestPlanCasesUpdateReqDTO();
                TestPlanCreateReqDTO.SelectedNode sn = new TestPlanCreateReqDTO.SelectedNode();
                sn.setDocumentId(UUID.fromString("00000000-0000-0000-0000-0000000000d1"));
                sn.setCaseIds(List.of(UUID.fromString("00000000-0000-0000-0000-0000000000c1")));
                reqDTO.setSelectedNodes(List.of(sn));

                assertThrows(ServiceException.class,
                                () -> planService.updatePlanCases(planId, userId, reqDTO));
                verify(projectAccessGuard).requireProjectMember(projectId, userId);
        }

        @Test
        void getPlanPlannedCases_returnsOriginalIds() {
                UUID docA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
                UUID caseC1 = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                TestPlanModuleSnapshot snapA = new TestPlanModuleSnapshot();
                snapA.setId(UUID.fromString("00000000-0000-0000-0000-0000000000aa"));
                snapA.setOriginalModuleId(docA);
                snapA.setType("document");
                when(planModuleSnapshotMapper.listByPlanIdAndType(planId, "document"))
                                .thenReturn(List.of(snapA));

                TestPlanNodeSnapshot caseSnap = new TestPlanNodeSnapshot();
                caseSnap.setId(UUID.fromString("00000000-0000-0000-0000-0000000000e2"));
                caseSnap.setOriginalNodeId(caseC1);
                caseSnap.setIsAssociated(true);
                when(planNodeSnapshotMapper.listAssociatedByPlanIdAndDocumentId(planId, snapA.getId()))
                                .thenReturn(List.of(caseSnap));

                List<PlannedCasesRespDTO> result = planService.getPlanPlannedCases(planId, userId);

                assertEquals(1, result.size());
                assertEquals(docA, result.get(0).getDocumentId());
                assertEquals(List.of(caseC1), result.get(0).getCaseIds());
                verify(projectAccessGuard).requireProjectMember(projectId, userId);
        }

        @Test
        void submitExecutionRecord_invalidResult_throws() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setStatus("in_progress");

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                TestPlanNodeSnapshot snapshot = new TestPlanNodeSnapshot();
                snapshot.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
                snapshot.setPlanId(planId);
                snapshot.setIsAssociated(true);
                snapshot.setType("case");

                when(planNodeSnapshotMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000004")))
                                .thenReturn(snapshot);

                TestPlanRecordReqDTO reqDTO = new TestPlanRecordReqDTO();
                reqDTO.setSnapshotNodeId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
                reqDTO.setResult("blocked");

                assertThrows(ServiceException.class,
                                () -> planService.submitExecutionRecord(planId, userId, reqDTO));
        }

        @Test
        void submitExecutionRecord_untested_resetsResult() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setStatus("in_progress");

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                TestPlanNodeSnapshot snapshot = new TestPlanNodeSnapshot();
                snapshot.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
                snapshot.setPlanId(planId);
                snapshot.setIsAssociated(true);
                snapshot.setType("case");
                snapshot.setLastResult("pass");

                when(planNodeSnapshotMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000004")))
                                .thenReturn(snapshot);

                TestPlanRecordReqDTO reqDTO = new TestPlanRecordReqDTO();
                reqDTO.setSnapshotNodeId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
                reqDTO.setResult("untested");

                planService.submitExecutionRecord(planId, userId, reqDTO);

                ArgumentCaptor<TestPlanNodeSnapshot> captor = ArgumentCaptor.forClass(TestPlanNodeSnapshot.class);
                verify(planNodeSnapshotMapper).updateById(captor.capture());
                assertEquals("untested", captor.getValue().getLastResult());
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
        void submitExecutionRecord_snapshotNotFound_throws() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setStatus("in_progress");

                when(testPlanMapper.selectById(planId)).thenReturn(plan);
                when(planNodeSnapshotMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000004")))
                                .thenReturn(null);

                TestPlanRecordReqDTO reqDTO = new TestPlanRecordReqDTO();
                reqDTO.setSnapshotNodeId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
                reqDTO.setResult("pass");

                assertThrows(ServiceException.class,
                                () -> planService.submitExecutionRecord(planId, userId, reqDTO));
        }

        @Test
        void submitExecutionRecord_notAssociated_throws() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setStatus("in_progress");

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                TestPlanNodeSnapshot snapshot = new TestPlanNodeSnapshot();
                snapshot.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
                snapshot.setPlanId(planId);
                snapshot.setIsAssociated(false);
                snapshot.setType("case");

                when(planNodeSnapshotMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000004")))
                                .thenReturn(snapshot);

                TestPlanRecordReqDTO reqDTO = new TestPlanRecordReqDTO();
                reqDTO.setSnapshotNodeId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
                reqDTO.setResult("pass");

                assertThrows(ServiceException.class,
                                () -> planService.submitExecutionRecord(planId, userId, reqDTO));
        }

        @Test
        void submitExecutionRecord_notCaseType_throws() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setStatus("in_progress");

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                TestPlanNodeSnapshot snapshot = new TestPlanNodeSnapshot();
                snapshot.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
                snapshot.setPlanId(planId);
                snapshot.setIsAssociated(true);
                snapshot.setType("normal");

                when(planNodeSnapshotMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000004")))
                                .thenReturn(snapshot);

                TestPlanRecordReqDTO reqDTO = new TestPlanRecordReqDTO();
                reqDTO.setSnapshotNodeId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
                reqDTO.setResult("pass");

                assertThrows(ServiceException.class,
                                () -> planService.submitExecutionRecord(planId, userId, reqDTO));
        }

        @Test
        void getNodeExecutionRecords_success() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                TestPlanExecutionRecord record = new TestPlanExecutionRecord();
                record.setId(UUID.fromString("00000000-0000-0000-0000-000000000005"));
                record.setPlanId(planId);
                record.setSnapshotNodeId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
                record.setExecutorId(userId);
                record.setResult("pass");

                when(planExecutionRecordMapper.listByPlanIdAndNodeId(planId,
                                UUID.fromString("00000000-0000-0000-0000-000000000004")))
                                .thenReturn(List.of(record));
                when(userMapper.selectById(userId)).thenReturn(null);

                List<TestPlanExecutionRecordRespDTO> result = planService.getNodeExecutionRecords(planId,
                                UUID.fromString("00000000-0000-0000-0000-000000000004"), userId);

                assertNotNull(result);
                assertEquals(1, result.size());
                assertEquals("pass", result.get(0).getResult());
                verify(projectAccessGuard).requireProjectMember(projectId, userId);
        }

        @Test
        void syncPlan_success() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setExecutorId(userId);
                plan.setStatus("in_progress");

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                TestPlanNodeSnapshot snapshot = new TestPlanNodeSnapshot();
                snapshot.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
                snapshot.setPlanId(planId);
                snapshot.setOriginalNodeId(UUID.fromString("00000000-0000-0000-0000-000000000006"));
                snapshot.setTitle("Old Title");
                snapshot.setType("normal");

                when(planNodeSnapshotMapper.listByPlanId(planId))
                                .thenReturn(List.of(snapshot));

                TestCaseNode currentNode = new TestCaseNode();
                currentNode.setId(UUID.fromString("00000000-0000-0000-0000-000000000006"));
                currentNode.setTitle("Updated Title");
                currentNode.setType("case");
                currentNode.setPriority("high");
                currentNode.setSortOrder(0);
                currentNode.setIsDeleted(false);

                when(testCaseNodeMapper.listByIds(anyCollection()))
                                .thenReturn(List.of(currentNode));

                planService.syncPlan(planId, userId);

                ArgumentCaptor<TestPlanNodeSnapshot> nodeCaptor = ArgumentCaptor.forClass(TestPlanNodeSnapshot.class);
                verify(planNodeSnapshotMapper).updateById(nodeCaptor.capture());
                assertEquals("Updated Title", nodeCaptor.getValue().getTitle());
                assertEquals("case", nodeCaptor.getValue().getType());

                ArgumentCaptor<TestPlan> planCaptor = ArgumentCaptor.forClass(TestPlan.class);
                verify(testPlanMapper).updateById(planCaptor.capture());
                assertEquals(planId, planCaptor.getValue().getId());
                assertNotNull(planCaptor.getValue().getSnapshotSyncedAt());
        }

        @Test
        void syncPlan_notExecutor_throws() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setExecutorId(otherUserId);
                plan.setStatus("in_progress");

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                assertThrows(ServiceException.class,
                                () -> planService.syncPlan(planId, userId));
        }

        @Test
        void syncPlan_notInProgress_throws() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setExecutorId(userId);
                plan.setStatus("completed");

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                assertThrows(ServiceException.class,
                                () -> planService.syncPlan(planId, userId));
        }

        @Test
        void syncPlan_deletedOriginal_marksDeleted() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setExecutorId(userId);
                plan.setStatus("in_progress");

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                TestPlanNodeSnapshot snapshot = new TestPlanNodeSnapshot();
                snapshot.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
                snapshot.setPlanId(planId);
                snapshot.setOriginalNodeId(UUID.fromString("00000000-0000-0000-0000-000000000006"));

                when(planNodeSnapshotMapper.listByPlanId(planId))
                                .thenReturn(List.of(snapshot));
                when(testCaseNodeMapper.listByIds(anyCollection()))
                                .thenReturn(List.of());

                planService.syncPlan(planId, userId);

                ArgumentCaptor<TestPlanNodeSnapshot> delCaptor = ArgumentCaptor.forClass(TestPlanNodeSnapshot.class);
                verify(planNodeSnapshotMapper).updateById(delCaptor.capture());
                assertTrue(delCaptor.getValue().getIsDeleted());
        }

        @Test
        void closePlan_success() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setExecutorId(userId);
                plan.setStatus("in_progress");

                when(testPlanMapper.selectById(planId)).thenReturn(plan);
                when(planNodeSnapshotMapper.countUntestedAssociatedByPlanId(planId, Constants.Status.UNTESTED))
                                .thenReturn(0L);

                planService.closePlan(planId, userId);

                ArgumentCaptor<TestPlan> captor = ArgumentCaptor.forClass(TestPlan.class);
                verify(testPlanMapper).updateById(captor.capture());
                assertEquals("closed", captor.getValue().getStatus());
        }

        @Test
        void closePlan_withUntestedCases_warns() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setExecutorId(userId);
                plan.setStatus("in_progress");

                when(testPlanMapper.selectById(planId)).thenReturn(plan);
                when(planNodeSnapshotMapper.countUntestedAssociatedByPlanId(planId, Constants.Status.UNTESTED))
                                .thenReturn(3L);

                planService.closePlan(planId, userId);

                ArgumentCaptor<TestPlan> captor = ArgumentCaptor.forClass(TestPlan.class);
                verify(testPlanMapper).updateById(captor.capture());
                assertEquals("closed", captor.getValue().getStatus());
        }

        @Test
        void closePlan_notExecutor_throws() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setExecutorId(otherUserId);

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

        // ========== completePlan / deletePlan ==========

        @Test
        void completePlan_success() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setExecutorId(userId);
                plan.setStatus(Constants.Status.IN_PROGRESS);

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                planService.completePlan(planId, userId);

                ArgumentCaptor<TestPlan> captor = ArgumentCaptor.forClass(TestPlan.class);
                verify(testPlanMapper).updateById(captor.capture());
                assertEquals(Constants.Status.COMPLETED, captor.getValue().getStatus());
        }

        @Test
        void completePlan_notFound_throws() {
                when(testPlanMapper.selectById(planId)).thenReturn(null);

                assertThrows(ServiceException.class,
                                () -> planService.completePlan(planId, userId));
        }

        @Test
        void completePlan_notExecutor_throws() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setExecutorId(otherUserId);
                plan.setStatus(Constants.Status.IN_PROGRESS);

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                assertThrows(ServiceException.class,
                                () -> planService.completePlan(planId, userId));
        }

        @Test
        void deletePlan_success() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setExecutorId(userId);

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                planService.deletePlan(planId, userId);

                verify(planExecutionRecordMapper).deleteByPlanId(planId);
                verify(planNodeSnapshotMapper).deleteByPlanId(planId);
                verify(planModuleSnapshotMapper).deleteByPlanId(planId);
                verify(testPlanMapper).deleteById(planId);
        }

        @Test
        void deletePlan_notExecutor_throws() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setExecutorId(otherUserId);

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                assertThrows(ServiceException.class,
                                () -> planService.deletePlan(planId, userId));
                verify(testPlanMapper, never()).deleteById(any(UUID.class));
        }

        @Test
        void deletePlan_notFound_throws() {
                when(testPlanMapper.selectById(planId)).thenReturn(null);

                assertThrows(ServiceException.class,
                                () -> planService.deletePlan(planId, userId));
        }

        // ========== getPlanProgress ==========

        @Test
        void getPlanProgress_success() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                TestPlanNodeSnapshot snap1 = new TestPlanNodeSnapshot();
                snap1.setLastResult("pass");
                TestPlanNodeSnapshot snap2 = new TestPlanNodeSnapshot();
                snap2.setLastResult("fail");
                TestPlanNodeSnapshot snap3 = new TestPlanNodeSnapshot();
                snap3.setLastResult(null);

                when(planNodeSnapshotMapper.listAssociatedByPlanId(planId, Constants.NodeType.CASE))
                                .thenReturn(List.of(snap1, snap2, snap3));

                TestPlanProgressRespDTO result = planService.getPlanProgress(planId, userId);

                assertEquals(3, result.getTotalAssociated());
                assertEquals(1, result.getPassed());
                assertEquals(1, result.getFailed());
                assertEquals(1, result.getUntested());
                verify(projectAccessGuard).requireProjectMember(projectId, userId);
        }

        @Test
        void getPlanProgress_notFound_throws() {
                when(testPlanMapper.selectById(planId)).thenReturn(null);

                assertThrows(ServiceException.class,
                                () -> planService.getPlanProgress(planId, userId));
                verify(projectAccessGuard, never()).requireProjectMember(any(), any());
        }

        @Test
        void getPlanProgress_emptySnapshots() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
                when(testPlanMapper.selectById(planId)).thenReturn(plan);
                when(planNodeSnapshotMapper.listAssociatedByPlanId(planId, Constants.NodeType.CASE))
                                .thenReturn(new ArrayList<>());

                TestPlanProgressRespDTO result = planService.getPlanProgress(planId, userId);

                assertEquals(0, result.getTotalAssociated());
                assertEquals(0.0, result.getProgressPercent());
                verify(projectAccessGuard).requireProjectMember(projectId, userId);
        }

        // ========== syncPlan module snapshot ==========

        @Test
        void syncPlan_syncsModuleName() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setExecutorId(userId);
                plan.setStatus("in_progress");

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                UUID moduleSnapId = UUID.randomUUID();
                TestPlanModuleSnapshot moduleSnap = new TestPlanModuleSnapshot();
                moduleSnap.setId(moduleSnapId);
                moduleSnap.setOriginalModuleId(UUID.fromString("00000000-0000-0000-0000-000000000010"));
                moduleSnap.setName("old name");
                moduleSnap.setSortOrder(1);

                when(planModuleSnapshotMapper.listByPlanId(planId))
                                .thenReturn(List.of(moduleSnap));
                when(planNodeSnapshotMapper.listByPlanId(planId))
                                .thenReturn(new ArrayList<>());

                TestCaseModule originalModule = new TestCaseModule();
                originalModule.setId(UUID.fromString("00000000-0000-0000-0000-000000000010"));
                originalModule.setName("new name");
                originalModule.setSortOrder(2);
                originalModule.setIsDeleted(false);
                when(testCaseModuleMapper.listByIds(anyCollection()))
                                .thenReturn(List.of(originalModule));

                planService.syncPlan(planId, userId);

                ArgumentCaptor<TestPlanModuleSnapshot> moduleCaptor = ArgumentCaptor.forClass(TestPlanModuleSnapshot.class);
                verify(planModuleSnapshotMapper).updateById(moduleCaptor.capture());
                assertEquals("new name", moduleCaptor.getValue().getName());
                assertEquals(2, moduleCaptor.getValue().getSortOrder());
        }

        @Test
        void syncPlan_deletesRemovedModule() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setExecutorId(userId);
                plan.setStatus("in_progress");

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                UUID moduleSnapId = UUID.randomUUID();
                TestPlanModuleSnapshot moduleSnap = new TestPlanModuleSnapshot();
                moduleSnap.setId(moduleSnapId);
                moduleSnap.setOriginalModuleId(UUID.fromString("00000000-0000-0000-0000-000000000010"));

                when(planModuleSnapshotMapper.listByPlanId(planId))
                                .thenReturn(List.of(moduleSnap));
                when(planNodeSnapshotMapper.listByPlanId(planId))
                                .thenReturn(new ArrayList<>());
                when(testCaseModuleMapper.listByIds(anyCollection()))
                                .thenReturn(List.of());

                planService.syncPlan(planId, userId);

                verify(planModuleSnapshotMapper).deleteById(moduleSnapId);
        }

        @Test
        void syncPlan_deletedModule_cascadesNodeDeletion() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setExecutorId(userId);
                plan.setStatus("in_progress");

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                UUID moduleSnapId = UUID.randomUUID();
                TestPlanModuleSnapshot moduleSnap = new TestPlanModuleSnapshot();
                moduleSnap.setId(moduleSnapId);
                moduleSnap.setOriginalModuleId(UUID.fromString("00000000-0000-0000-0000-000000000010"));

                UUID nodeSnapId = UUID.randomUUID();
                TestPlanNodeSnapshot nodeSnap = new TestPlanNodeSnapshot();
                nodeSnap.setId(nodeSnapId);
                nodeSnap.setDocumentSnapshotId(moduleSnapId);

                when(planModuleSnapshotMapper.listByPlanId(planId))
                                .thenReturn(List.of(moduleSnap));
                when(planNodeSnapshotMapper.listByPlanId(planId))
                                .thenReturn(List.of(nodeSnap));
                when(testCaseModuleMapper.listByIds(anyCollection()))
                                .thenReturn(List.of());

                planService.syncPlan(planId, userId);

                verify(planModuleSnapshotMapper).deleteById(moduleSnapId);
                verify(planNodeSnapshotMapper).deleteById(nodeSnapId);
        }
}
