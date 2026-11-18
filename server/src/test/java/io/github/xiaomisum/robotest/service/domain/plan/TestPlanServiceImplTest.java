package io.github.xiaomisum.robotest.service.domain.plan;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.convert.TestPlanConvertMapper;
import io.github.xiaomisum.robotest.model.convert.TestPlanConvertMapperImpl;
import io.github.xiaomisum.robotest.model.dto.request.plan.TestPlanCasesUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.plan.TestPlanCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.plan.TestPlanRecordReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.plan.TestPlanUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.PlannedCasesRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanSnapshotNodeRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlan;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlanExecutionRecord;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlanModuleSnapshot;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlanNodeSnapshot;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.repository.plan.TestPlanMapper;
import io.github.xiaomisum.robotest.repository.plan.TestPlanModuleSnapshotMapper;
import io.github.xiaomisum.robotest.repository.plan.TestPlanNodeSnapshotMapper;
import io.github.xiaomisum.robotest.repository.plan.TestPlanExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import io.github.xiaomisum.robotest.service.project.ProjectActivityService;
import io.github.xiaomisum.robotest.service.domain.tcasedoc.TestCaseRequirementService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanProgressRespDTO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        private TestCaseDocumentMapper testCaseDocumentMapper;
        @Mock
        private ProjectModuleMapper projectModuleMapper;
        @Mock
        private TestCaseNodeMapper testCaseNodeMapper;
        @Mock
        private SysUserMapper userMapper;
        @Mock
        private RequirementMapper requirementMapper;
        @Mock
        private TestCaseRequirementService testCaseRequirementService;
        @Mock
        private ProjectAccessGuard projectAccessGuard;
        @Mock
        private ProjectActivityService projectActivityService;
        @Mock
        private ProjectMapper projectMapper;
        @Mock
        private WorkspaceUserMapper workspaceUserMapper;
        @Mock
        private TestPlanService self;
        @Spy
        private TestPlanConvertMapper testPlanConvertMapper = new TestPlanConvertMapperImpl();

        @InjectMocks
        private TestPlanServiceImpl planService;

        private UUID projectId;
        private UUID userId;
        private UUID planId;
        private UUID otherUserId;

        /** 纯单测无 MyBatis 启动：wrapper 的 lambda 列解析依赖 TableInfo 缓存 */
        @BeforeAll
        static void initTableInfo() {
                MybatisConfiguration configuration = new MybatisConfiguration();
                configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
                MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
                if (TableInfoHelper.getTableInfo(TestPlan.class) == null) {
                        TableInfoHelper.initTableInfo(assistant, TestPlan.class);
                }
        }

        @BeforeEach
        void setUp() {
                projectId = UUID.fromString("00000000-0000-0000-0000-000000000001");
                userId = UUID.fromString("00000000-0000-0000-0000-000000000002");
                planId = UUID.fromString("00000000-0000-0000-0000-000000000003");
                otherUserId = UUID.fromString("00000000-0000-0000-0000-000000000011");
                lenient().when(testCaseRequirementService.resolvePlannedCases(any(), any())).thenReturn(Map.of());
                lenient().when(testCaseRequirementService.listRequirementRefs(any(), any())).thenReturn(List.of());
        }

        @Test
        void getPlanPage_success() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
                plan.setName("Plan 1");
                plan.setStatus("new");
                plan.setExecutorId(userId);

                PageResult<TestPlan> page = new PageResult<>(List.of(plan), 1L);
                doReturn(page).when(testPlanMapper).findPage(any(PageParam.class), eq(projectId), isNull(), isNull());

                SysUser executor = new SysUser();
                executor.setId(userId);
                executor.setUsername("executor");
                executor.setName("赵六");
                when(userMapper.selectById(userId)).thenReturn(executor);

                PageResult<TestPlanListRespDTO> result = planService.getPlanPage(
                                projectId, userId, null, null, 1, 10);

                assertNotNull(result);
                assertEquals(1, result.getList().size());
                assertEquals("Plan 1", result.getList().get(0).getName());
                assertEquals("赵六", result.getList().get(0).getExecutor().getName());
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
        void createPlan_fromRequirements_persistsRequirementIds() {
                doAnswer(inv -> {
                        ((TestPlan) inv.getArgument(0)).setId(UUID.randomUUID());
                        return 1;
                }).when(testPlanMapper).insert(any(TestPlan.class));

                UUID requirementId = UUID.randomUUID();
                UUID documentId = UUID.randomUUID();
                UUID caseId = UUID.randomUUID();
                when(testCaseRequirementService.resolvePlannedCases(projectId, List.of(requirementId)))
                        .thenReturn(Map.of(documentId, Set.of(caseId)));

                TestPlanCreateReqDTO reqDTO = new TestPlanCreateReqDTO();
                reqDTO.setName("Requirement Plan");
                reqDTO.setRequirementIds(List.of(requirementId));

                TestPlanDetailRespDTO result = planService.createPlan(projectId, userId, reqDTO);

                assertNotNull(result);
                ArgumentCaptor<TestPlan> captor = ArgumentCaptor.forClass(TestPlan.class);
                verify(testPlanMapper).insert(captor.capture());
                assertEquals(List.of(requirementId), captor.getValue().getRequirementIds());
                verify(testCaseRequirementService).resolvePlannedCases(projectId, List.of(requirementId));
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

                TestPlanDetailRespDTO result = planService.getPlanDetail(projectId, planId, userId);

                assertNotNull(result);
                assertEquals("Plan", result.getName());
                verify(projectAccessGuard).requireProjectMember(projectId, userId);
        }

        @Test
        void getPlanDetail_notFound_throws() {
                when(testPlanMapper.selectById(planId)).thenReturn(null);

                assertThrows(ServiceException.class,
                                () -> planService.getPlanDetail(projectId, planId, userId));
                verify(projectAccessGuard, never()).requireProjectMember(any(), any());
        }

        @Test
        void getPlanDetail_crossProject_throws() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(UUID.randomUUID());
                plan.setExecutorId(userId);
                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                // 归属活动项目校验（SEC-014）：跨项目按不存在处理，不泄露计划存在性
                ServiceException exception = assertThrows(ServiceException.class,
                                () -> planService.getPlanDetail(projectId, planId, userId));
                assertEquals(ErrorCodeConstants.TEST_PLAN_NOT_FOUND.code(), exception.getCode());
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

                List<TestPlanSnapshotNodeRespDTO> result = planService.getPlanSnapshotTree(projectId, planId, null, userId);

                assertNotNull(result);
                assertFalse(result.isEmpty());
                verify(projectAccessGuard).requireProjectMember(projectId, userId);
        }

        @Test
        void getPlanSnapshotTree_notFound_throws() {
                when(testPlanMapper.selectById(planId)).thenReturn(null);

                assertThrows(ServiceException.class,
                                () -> planService.getPlanSnapshotTree(projectId, planId, null, userId));
                verify(projectAccessGuard, never()).requireProjectMember(any(), any());
        }

        @Test
        void submitExecutionRecord_success() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
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

                planService.submitExecutionRecord(projectId, planId, userId, reqDTO);

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
                plan.setProjectId(projectId);
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

                planService.submitExecutionRecord(projectId, planId, userId, reqDTO);

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
                                () -> planService.updatePlanCases(projectId, planId, userId, reqDTO));
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

                List<PlannedCasesRespDTO> result = planService.getPlanPlannedCases(projectId, planId, userId);

                assertEquals(1, result.size());
                assertEquals(docA, result.get(0).getDocumentId());
                assertEquals(List.of(caseC1), result.get(0).getCaseIds());
                verify(projectAccessGuard).requireProjectMember(projectId, userId);
        }

        @Test
        void submitExecutionRecord_invalidResult_throws() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
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
                                () -> planService.submitExecutionRecord(projectId, planId, userId, reqDTO));
        }

        @Test
        void submitExecutionRecord_untested_resetsResult() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
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

                planService.submitExecutionRecord(projectId, planId, userId, reqDTO);

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
                                () -> planService.submitExecutionRecord(projectId, planId, userId, reqDTO));
        }

        @Test
        void submitExecutionRecord_snapshotNotFound_throws() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
                plan.setStatus("in_progress");

                when(testPlanMapper.selectById(planId)).thenReturn(plan);
                when(planNodeSnapshotMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000004")))
                                .thenReturn(null);

                TestPlanRecordReqDTO reqDTO = new TestPlanRecordReqDTO();
                reqDTO.setSnapshotNodeId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
                reqDTO.setResult("pass");

                assertThrows(ServiceException.class,
                                () -> planService.submitExecutionRecord(projectId, planId, userId, reqDTO));
        }

        @Test
        void submitExecutionRecord_notAssociated_throws() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
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
                                () -> planService.submitExecutionRecord(projectId, planId, userId, reqDTO));
        }

        @Test
        void submitExecutionRecord_notCaseType_throws() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
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
                                () -> planService.submitExecutionRecord(projectId, planId, userId, reqDTO));
        }

        @Test
        void syncPlan_success() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
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

                planService.syncPlan(projectId, planId, userId);

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
        void syncPlan_nonExecutor_success() {
                // 功能测试模块不做权限码校验，服务层只校验归属与状态
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
                plan.setExecutorId(otherUserId);
                plan.setStatus("in_progress");

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                planService.syncPlan(projectId, planId, userId);

                verify(testPlanMapper).updateById(any(TestPlan.class));
        }

        @Test
        void syncPlan_notInProgress_throws() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
                plan.setExecutorId(userId);
                plan.setStatus("completed");

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                assertThrows(ServiceException.class,
                                () -> planService.syncPlan(projectId, planId, userId));
        }

        @Test
        void syncPlan_deletedOriginal_marksDeleted() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
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

                planService.syncPlan(projectId, planId, userId);

                ArgumentCaptor<TestPlanNodeSnapshot> delCaptor = ArgumentCaptor.forClass(TestPlanNodeSnapshot.class);
                verify(planNodeSnapshotMapper).updateById(delCaptor.capture());
                assertTrue(delCaptor.getValue().getIsDeleted());
        }

        // ========== completePlan / deletePlan ==========

        @Test
        void completePlan_success() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
                plan.setExecutorId(userId);
                plan.setStatus(Constants.Status.IN_PROGRESS);

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                planService.completePlan(projectId, planId, userId);

                ArgumentCaptor<TestPlan> captor = ArgumentCaptor.forClass(TestPlan.class);
                verify(testPlanMapper).updateById(captor.capture());
                assertEquals(Constants.Status.COMPLETED, captor.getValue().getStatus());
        }

        @Test
        void completePlan_notFound_throws() {
                when(testPlanMapper.selectById(planId)).thenReturn(null);

                assertThrows(ServiceException.class,
                                () -> planService.completePlan(projectId, planId, userId));
        }

        @Test
        void completePlan_nonExecutor_success() {
                // 功能测试模块不做权限码校验
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
                plan.setExecutorId(otherUserId);
                plan.setStatus(Constants.Status.IN_PROGRESS);

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                planService.completePlan(projectId, planId, userId);

                ArgumentCaptor<TestPlan> captor = ArgumentCaptor.forClass(TestPlan.class);
                verify(testPlanMapper).updateById(captor.capture());
                assertEquals(Constants.Status.COMPLETED, captor.getValue().getStatus());
        }

        @Test
        void deletePlan_success() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
                plan.setExecutorId(userId);

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                planService.deletePlan(projectId, planId, userId);

                verify(planExecutionRecordMapper).deleteByPlanId(planId);
                verify(planNodeSnapshotMapper).deleteByPlanId(planId);
                verify(planModuleSnapshotMapper).deleteByPlanId(planId);
                verify(testPlanMapper).deleteById(planId);
        }

        @Test
        void deletePlan_notExecutor_throws() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
                plan.setExecutorId(otherUserId);

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                assertThrows(ServiceException.class,
                                () -> planService.deletePlan(projectId, planId, userId));
                verify(testPlanMapper, never()).deleteById(any(UUID.class));
        }

        @Test
        void deletePlan_notFound_throws() {
                when(testPlanMapper.selectById(planId)).thenReturn(null);

                assertThrows(ServiceException.class,
                                () -> planService.deletePlan(projectId, planId, userId));
        }

        // ========== blockPlan / resumePlan ==========

        @Test
        void blockPlan_success() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
                plan.setExecutorId(userId);
                plan.setStatus(Constants.Status.IN_PROGRESS);
                plan.setName("回归计划");

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                planService.blockPlan(projectId, planId, userId);

                ArgumentCaptor<TestPlan> captor = ArgumentCaptor.forClass(TestPlan.class);
                verify(testPlanMapper).updateById(captor.capture());
                assertEquals(planId, captor.getValue().getId());
                assertEquals(Constants.Status.BLOCKED, captor.getValue().getStatus());
                verify(projectActivityService).record(eq(projectId), eq(userId), eq("TEST_PLAN"),
                                eq(planId), eq("回归计划"), eq("PLAN_BLOCKED"), anyString());
        }

        @Test
        void blockPlan_newPlan_success() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
                plan.setExecutorId(userId);
                plan.setStatus(Constants.Status.NEW);

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                planService.blockPlan(projectId, planId, userId);

                ArgumentCaptor<TestPlan> captor = ArgumentCaptor.forClass(TestPlan.class);
                verify(testPlanMapper).updateById(captor.capture());
                assertEquals(Constants.Status.BLOCKED, captor.getValue().getStatus());
        }

        @Test
        void blockPlan_nonExecutor_success() {
                // 功能测试模块不做权限码校验
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
                plan.setExecutorId(otherUserId);
                plan.setStatus(Constants.Status.IN_PROGRESS);

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                planService.blockPlan(projectId, planId, userId);

                ArgumentCaptor<TestPlan> captor = ArgumentCaptor.forClass(TestPlan.class);
                verify(testPlanMapper).updateById(captor.capture());
                assertEquals(Constants.Status.BLOCKED, captor.getValue().getStatus());
        }

        @Test
        void blockPlan_completed_throws() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
                plan.setExecutorId(userId);
                plan.setStatus(Constants.Status.COMPLETED);

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                ServiceException exception = assertThrows(ServiceException.class,
                                () -> planService.blockPlan(projectId, planId, userId));
                assertEquals(ErrorCodeConstants.PLAN_ONLY_ACTIVE_CAN_BLOCK.code(), exception.getCode());
        }

        @Test
        void resumePlan_success() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
                plan.setExecutorId(userId);
                plan.setStatus(Constants.Status.BLOCKED);
                plan.setName("回归计划");

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                planService.resumePlan(projectId, planId, userId);

                ArgumentCaptor<TestPlan> captor = ArgumentCaptor.forClass(TestPlan.class);
                verify(testPlanMapper).updateById(captor.capture());
                assertEquals(planId, captor.getValue().getId());
                assertEquals(Constants.Status.IN_PROGRESS, captor.getValue().getStatus());
                verify(projectActivityService).record(eq(projectId), eq(userId), eq("TEST_PLAN"),
                                eq(planId), eq("回归计划"), eq("PLAN_RESUMED"), anyString());
        }

        @Test
        void resumePlan_notBlocked_throws() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
                plan.setExecutorId(userId);
                plan.setStatus(Constants.Status.IN_PROGRESS);

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                ServiceException exception = assertThrows(ServiceException.class,
                                () -> planService.resumePlan(projectId, planId, userId));
                assertEquals(ErrorCodeConstants.PLAN_ONLY_BLOCKED_CAN_RESUME.code(), exception.getCode());
                verify(testPlanMapper, never()).updateById(any(TestPlan.class));
        }

        @Test
        void resumePlan_nonExecutor_success() {
                // 功能测试模块不做权限码校验
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
                plan.setExecutorId(otherUserId);
                plan.setStatus(Constants.Status.BLOCKED);

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                planService.resumePlan(projectId, planId, userId);

                ArgumentCaptor<TestPlan> captor = ArgumentCaptor.forClass(TestPlan.class);
                verify(testPlanMapper).updateById(captor.capture());
                assertEquals(Constants.Status.IN_PROGRESS, captor.getValue().getStatus());
        }

        @Test
        void completePlan_blocked_throws() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
                plan.setExecutorId(userId);
                plan.setStatus(Constants.Status.BLOCKED);

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                ServiceException exception = assertThrows(ServiceException.class,
                                () -> planService.completePlan(projectId, planId, userId));
                assertEquals(ErrorCodeConstants.PLAN_IS_BLOCKED.code(), exception.getCode());
                verify(testPlanMapper, never()).updateById(any(TestPlan.class));
        }

        @Test
        void syncPlan_blocked_throws() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
                plan.setExecutorId(userId);
                plan.setStatus(Constants.Status.BLOCKED);

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                ServiceException exception = assertThrows(ServiceException.class,
                                () -> planService.syncPlan(projectId, planId, userId));
                assertEquals(ErrorCodeConstants.PLAN_IS_BLOCKED.code(), exception.getCode());
                verify(planNodeSnapshotMapper, never()).listByPlanId(any());
        }

        @Test
        void updatePlanCases_blocked_throws() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
                plan.setExecutorId(userId);
                plan.setStatus(Constants.Status.BLOCKED);

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                ServiceException exception = assertThrows(ServiceException.class,
                                () -> planService.updatePlanCases(projectId, planId, userId,
                                                new TestPlanCasesUpdateReqDTO()));
                assertEquals(ErrorCodeConstants.PLAN_IS_BLOCKED.code(), exception.getCode());
        }

        @Test
        void submitExecutionRecord_blocked_throws() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
                plan.setExecutorId(userId);
                plan.setStatus(Constants.Status.BLOCKED);

                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                TestPlanRecordReqDTO reqDTO = new TestPlanRecordReqDTO();
                reqDTO.setSnapshotNodeId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
                reqDTO.setResult("pass");

                ServiceException exception = assertThrows(ServiceException.class,
                                () -> planService.submitExecutionRecord(projectId, planId, userId, reqDTO));
                assertEquals(ErrorCodeConstants.PLAN_IS_BLOCKED.code(), exception.getCode());
                verify(planExecutionRecordMapper, never()).insert(any(TestPlanExecutionRecord.class));
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

                TestPlanProgressRespDTO result = planService.getPlanProgress(projectId, planId, userId);

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
                                () -> planService.getPlanProgress(projectId, planId, userId));
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

                TestPlanProgressRespDTO result = planService.getPlanProgress(projectId, planId, userId);

                assertEquals(0, result.getTotalAssociated());
                assertEquals(0.0, result.getProgressPercent());
                verify(projectAccessGuard).requireProjectMember(projectId, userId);
        }

        // ========== syncPlan module snapshot ==========

        @Test
        void syncPlan_syncsModuleName() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
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

                ProjectModule originalModule = new ProjectModule();
                originalModule.setId(UUID.fromString("00000000-0000-0000-0000-000000000010"));
                originalModule.setName("new name");
                originalModule.setSortOrder(2);
                originalModule.setIsDeleted(false);
                when(projectModuleMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000010")))
                                .thenReturn(originalModule);

                planService.syncPlan(projectId, planId, userId);

                ArgumentCaptor<TestPlanModuleSnapshot> moduleCaptor = ArgumentCaptor.forClass(TestPlanModuleSnapshot.class);
                verify(planModuleSnapshotMapper).updateById(moduleCaptor.capture());
                assertEquals("new name", moduleCaptor.getValue().getName());
                assertEquals(2, moduleCaptor.getValue().getSortOrder());
        }

        @Test
        void syncPlan_deletesRemovedModule() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
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
                when(projectModuleMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000010")))
                                .thenReturn(null);

                planService.syncPlan(projectId, planId, userId);

                verify(planModuleSnapshotMapper).deleteById(moduleSnapId);
        }

        @Test
        void syncPlan_deletedModule_cascadesNodeDeletion() {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
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
                when(projectModuleMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000010")))
                                .thenReturn(null);

                planService.syncPlan(projectId, planId, userId);

                verify(planModuleSnapshotMapper).deleteById(moduleSnapId);
                verify(planNodeSnapshotMapper).deleteById(nodeSnapId);
        }

        // ========== updatePlan（编辑与异步重规划） ==========

        private TestPlan draftPlan(UUID... requirementIds) {
                TestPlan plan = new TestPlan();
                plan.setId(planId);
                plan.setProjectId(projectId);
                plan.setExecutorId(userId);
                plan.setStatus(Constants.Status.NEW);
                plan.setName("Old Name");
                plan.setDescription("Old Desc");
                plan.setRequirementIds(List.of(requirementIds));
                return plan;
        }

        private Requirement requirement(UUID id) {
                Requirement requirement = new Requirement();
                requirement.setId(id);
                requirement.setProjectId(projectId);
                return requirement;
        }

        @Test
        void updatePlan_partialUpdate_onlyWritesPassedFields() {
                UUID req = UUID.fromString("00000000-0000-0000-0000-000000000050");
                when(testPlanMapper.selectById(planId)).thenReturn(draftPlan(req));

                TestPlanUpdateReqDTO reqDTO = new TestPlanUpdateReqDTO();
                reqDTO.setName("New Name");

                planService.updatePlan(projectId, planId, userId, reqDTO);

                ArgumentCaptor<TestPlan> captor = ArgumentCaptor.forClass(TestPlan.class);
                verify(testPlanMapper).updateById(captor.capture());
                TestPlan update = captor.getValue();
                assertEquals(planId, update.getId());
                assertEquals("New Name", update.getName());
                assertNull(update.getDescription());
                assertNull(update.getExecutorId());
                assertNull(update.getStartTime());
                assertNull(update.getRequirementIds());
                assertNull(update.getReplanStatus());
                verify(self, never()).runReplanAsync(any(), any());
        }

        @Test
        void updatePlan_blocked_throws() {
                TestPlan plan = draftPlan();
                plan.setStatus(Constants.Status.BLOCKED);
                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                TestPlanUpdateReqDTO reqDTO = new TestPlanUpdateReqDTO();
                reqDTO.setName("New Name");

                ServiceException exception = assertThrows(ServiceException.class,
                                () -> planService.updatePlan(projectId, planId, userId, reqDTO));
                assertEquals(ErrorCodeConstants.PLAN_IS_BLOCKED.code(), exception.getCode());
                verify(testPlanMapper, never()).updateById(any(TestPlan.class));
        }

        @Test
        void updatePlan_finished_throws() {
                TestPlan plan = draftPlan();
                plan.setStatus(Constants.Status.CLOSED);
                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                TestPlanUpdateReqDTO reqDTO = new TestPlanUpdateReqDTO();
                reqDTO.setName("New Name");

                ServiceException exception = assertThrows(ServiceException.class,
                                () -> planService.updatePlan(projectId, planId, userId, reqDTO));
                assertEquals(ErrorCodeConstants.TEST_PLAN_FINISHED.code(), exception.getCode());
        }

        @Test
        void updatePlan_runningReplan_throws() {
                TestPlan plan = draftPlan();
                plan.setReplanStatus(Constants.ReplanStatus.RUNNING);
                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                TestPlanUpdateReqDTO reqDTO = new TestPlanUpdateReqDTO();
                reqDTO.setName("New Name");

                ServiceException exception = assertThrows(ServiceException.class,
                                () -> planService.updatePlan(projectId, planId, userId, reqDTO));
                assertEquals(ErrorCodeConstants.REPLAN_IN_PROGRESS.code(), exception.getCode());
                verify(testPlanMapper, never()).updateById(any(TestPlan.class));
        }

        @Test
        void updatePlan_requirementOrderChange_doesNotReplan() {
                UUID reqA = UUID.fromString("00000000-0000-0000-0000-000000000051");
                UUID reqB = UUID.fromString("00000000-0000-0000-0000-000000000052");
                when(testPlanMapper.selectById(planId)).thenReturn(draftPlan(reqA, reqB));
                when(requirementMapper.listByIds(anyCollection())).thenReturn(List.of(requirement(reqA), requirement(reqB)));

                TestPlanUpdateReqDTO reqDTO = new TestPlanUpdateReqDTO();
                reqDTO.setRequirementIds(List.of(reqB, reqA));
                reqDTO.setReplan(true);

                planService.updatePlan(projectId, planId, userId, reqDTO);

                ArgumentCaptor<TestPlan> captor = ArgumentCaptor.forClass(TestPlan.class);
                verify(testPlanMapper).updateById(captor.capture());
                assertNull(captor.getValue().getReplanStatus());
                verify(self, never()).runReplanAsync(any(), any());
                verifyNoInteractions(planModuleSnapshotMapper, planNodeSnapshotMapper, planExecutionRecordMapper);
        }

        @Test
        void updatePlan_requirementSetChanged_replanTriggers() {
                UUID oldReq = UUID.fromString("00000000-0000-0000-0000-000000000053");
                UUID newReq = UUID.fromString("00000000-0000-0000-0000-000000000054");
                when(testPlanMapper.selectById(planId)).thenReturn(draftPlan(oldReq));
                when(requirementMapper.listByIds(anyCollection())).thenReturn(List.of(requirement(newReq)));

                TestPlanUpdateReqDTO reqDTO = new TestPlanUpdateReqDTO();
                reqDTO.setRequirementIds(List.of(newReq));
                reqDTO.setReplan(true);

                planService.updatePlan(projectId, planId, userId, reqDTO);

                ArgumentCaptor<TestPlan> captor = ArgumentCaptor.forClass(TestPlan.class);
                verify(testPlanMapper).updateById(captor.capture());
                TestPlan update = captor.getValue();
                assertEquals(Constants.ReplanStatus.RUNNING, update.getReplanStatus());
                assertNotNull(update.getReplanRequestedAt());
                assertEquals(List.of(newReq), update.getRequirementIds());
                verify(testPlanMapper).update(isNull(), any(LambdaUpdateWrapperX.class));
                verify(self).runReplanAsync(projectId, planId);
        }

        @Test
        void updatePlan_requirementChangedReplanFalse_doesNotReplan() {
                UUID oldReq = UUID.fromString("00000000-0000-0000-0000-000000000055");
                UUID newReq = UUID.fromString("00000000-0000-0000-0000-000000000056");
                when(testPlanMapper.selectById(planId)).thenReturn(draftPlan(oldReq));
                when(requirementMapper.listByIds(anyCollection())).thenReturn(List.of(requirement(newReq)));

                TestPlanUpdateReqDTO reqDTO = new TestPlanUpdateReqDTO();
                reqDTO.setRequirementIds(List.of(newReq));
                reqDTO.setReplan(false);

                planService.updatePlan(projectId, planId, userId, reqDTO);

                ArgumentCaptor<TestPlan> captor = ArgumentCaptor.forClass(TestPlan.class);
                verify(testPlanMapper).updateById(captor.capture());
                assertEquals(List.of(newReq), captor.getValue().getRequirementIds());
                assertNull(captor.getValue().getReplanStatus());
                verify(self, never()).runReplanAsync(any(), any());
                verifyNoInteractions(planModuleSnapshotMapper, planNodeSnapshotMapper, planExecutionRecordMapper);
        }

        // ========== getReplanStatus / executeReplan / 状态回写 ==========

        @Test
        void getReplanStatus_nullStatus_mapsToIdle() {
                TestPlan plan = draftPlan();
                plan.setReplanStatus(null);
                when(testPlanMapper.selectById(planId)).thenReturn(plan);

                assertEquals(Constants.ReplanStatus.IDLE,
                                planService.getReplanStatus(projectId, planId, userId).getStatus());
        }

        @Test
        void runReplanAsync_success_marksSucceeded() {
                when(self.executeReplan(projectId, planId)).thenReturn("规划完成，共 5 个用例");

                planService.runReplanAsync(projectId, planId);

                InOrder inOrder = inOrder(self);
                inOrder.verify(self).executeReplan(projectId, planId);
                inOrder.verify(self).markReplanSucceeded(projectId, planId, "规划完成，共 5 个用例");
                verify(self, never()).markReplanFailed(any(), any(), any());
        }

        @Test
        void runReplanAsync_executeFails_marksFailed() {
                when(self.executeReplan(projectId, planId)).thenThrow(new IllegalStateException("boom"));

                planService.runReplanAsync(projectId, planId);

                verify(self).markReplanFailed(projectId, planId, "boom");
                verify(self, never()).markReplanSucceeded(any(), any(), any());
        }

        @Test
        void runReplanAsync_markFailedFails_doesNotPropagate() {
                when(self.executeReplan(projectId, planId)).thenThrow(new IllegalStateException("boom"));
                doThrow(new IllegalStateException("write failed"))
                                .when(self).markReplanFailed(projectId, planId, "boom");

                planService.runReplanAsync(projectId, planId);

                verify(self).markReplanFailed(projectId, planId, "boom");
        }

        @Test
        void executeReplan_filtersRequirementsByProject_andMarksSnapshotSynced() {
                UUID reqA = UUID.fromString("00000000-0000-0000-0000-000000000057");
                UUID reqB = UUID.fromString("00000000-0000-0000-0000-000000000058");
                when(testPlanMapper.selectById(planId)).thenReturn(draftPlan(reqA, reqB));

                Requirement foreign = new Requirement();
                foreign.setId(reqB);
                foreign.setProjectId(UUID.fromString("00000000-0000-0000-0000-000000000099"));
                when(requirementMapper.listByIds(anyCollection())).thenReturn(List.of(requirement(reqA), foreign));
                when(testCaseDocumentMapper.listByIds(anyCollection())).thenReturn(List.of());
                when(planModuleSnapshotMapper.listByPlanIdAndType(any(), any())).thenReturn(List.of());

                String message = planService.executeReplan(projectId, planId);

                verify(testCaseRequirementService).resolvePlannedCases(projectId, List.of(reqA));
                ArgumentCaptor<TestPlan> captor = ArgumentCaptor.forClass(TestPlan.class);
                verify(testPlanMapper).updateById(captor.capture());
                assertNotNull(captor.getValue().getSnapshotSyncedAt());
                assertTrue(message.contains("0 个用例"));
        }

        @Test
        void markReplanSucceeded_persistsSucceededStatus() {
                when(testPlanMapper.selectById(planId)).thenReturn(draftPlan());

                planService.markReplanSucceeded(projectId, planId, "done");

                ArgumentCaptor<TestPlan> captor = ArgumentCaptor.forClass(TestPlan.class);
                verify(testPlanMapper).updateById(captor.capture());
                assertEquals(Constants.ReplanStatus.SUCCEEDED, captor.getValue().getReplanStatus());
                assertEquals("done", captor.getValue().getReplanMessage());
                assertNotNull(captor.getValue().getReplanFinishedAt());
        }

        @Test
        void markReplanFailed_persistsFailedStatus() {
                when(testPlanMapper.selectById(planId)).thenReturn(draftPlan());

                planService.markReplanFailed(projectId, planId, null);

                ArgumentCaptor<TestPlan> captor = ArgumentCaptor.forClass(TestPlan.class);
                verify(testPlanMapper).updateById(captor.capture());
                assertEquals(Constants.ReplanStatus.FAILED, captor.getValue().getReplanStatus());
                assertEquals("重新规划失败", captor.getValue().getReplanMessage());
                assertNotNull(captor.getValue().getReplanFinishedAt());
        }
}
