package io.github.xiaomisum.robotest.service.ai.task;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTaskConfirmReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTaskExecutionCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTaskPageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTaskSubmitReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskConfirmRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskCountRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskEventsRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskProposalRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskAdoption;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskExecution;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskProposal;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskTraceEvent;
import io.github.xiaomisum.robotest.repository.ai.AiTaskAdoptionMapper;
import io.github.xiaomisum.robotest.repository.ai.AiTaskExecutionMapper;
import io.github.xiaomisum.robotest.repository.ai.AiTaskMapper;
import io.github.xiaomisum.robotest.repository.ai.AiTaskProposalMapper;
import io.github.xiaomisum.robotest.repository.ai.AiTaskTraceEventMapper;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiSettingsReader;
import io.github.xiaomisum.robotest.service.ai.task.core.BudgetGuard;
import io.github.xiaomisum.robotest.service.ai.task.core.ExecutionLifecycle;
import io.github.xiaomisum.robotest.service.ai.task.core.TaskExecutionDispatcher;
import io.github.xiaomisum.robotest.service.ai.task.core.TraceRecorder;
import io.github.xiaomisum.robotest.service.ai.task.type.TaskTypeAdapter;
import io.github.xiaomisum.robotest.service.ai.task.type.TaskTypeAdapterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 统一任务资源（总册 3.6）单测：提交（创建即启动）、列表 / 详情、轨迹、终止 / 再次执行、
 * 提案确认逐项事务与跨层查询入口。
 */
@ExtendWith(MockitoExtension.class)
class AiTaskServiceImplTest {

    private static final UUID TASK_ID = UUID.randomUUID();
    private static final UUID EXEC_ID = UUID.randomUUID();
    private static final UUID PREV_EXEC_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_ID = UUID.randomUUID();
    private static final UUID MODEL_ID = UUID.randomUUID();
    private static final String TYPE = "requirement_import";
    private static final Map<String, Object> BUDGET = Map.of(
            "maxSteps", 40, "maxDurationSeconds", 600, "maxTokens", 200000, "maxCost", 5);

    @Mock
    private AiTaskMapper taskMapper;
    @Mock
    private AiTaskExecutionMapper executionMapper;
    @Mock
    private AiTaskProposalMapper proposalMapper;
    @Mock
    private AiTaskAdoptionMapper adoptionMapper;
    @Mock
    private AiTaskTraceEventMapper traceEventMapper;
    @Mock
    private AiSettingsReader settingsReader;
    @Mock
    private TaskTypeAdapterRegistry adapterRegistry;
    @Mock
    private BudgetGuard budgetGuard;
    @Mock
    private ProposalAdopterRegistry adopterRegistry;
    @Mock
    private TaskExecutionDispatcher executionDispatcher;
    @Mock
    private TraceRecorder traceRecorder;
    @Mock
    private ExecutionLifecycle executionLifecycle;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private RequirementMapper requirementMapper;
    @Mock
    private TaskTypeAdapter adapter;

    @InjectMocks
    private AiTaskServiceImpl service;

    private LoginUser loginUser;

    @BeforeEach
    void setUp() {
        loginUser = new LoginUser();
        loginUser.setId(USER_ID);
        loginUser.setAuthorities(List.of(new SimpleGrantedAuthority("ai:task"),
                new SimpleGrantedAuthority("ai:confirm")));
        loginUser.setWorkspaceAuthorities(new ArrayList<>());
        loginUser.setActiveWorkspaceId(WORKSPACE_ID);
        loginUser.setActiveProjectId(PROJECT_ID);

        lenient().when(settingsReader.settings())
                .thenReturn(new AiSettingsReader.AiSettings(true, MODEL_ID, 600, 2));
        lenient().when(settingsReader.usableModel()).thenReturn(model());
        lenient().when(adapterRegistry.get(TYPE)).thenReturn(adapter);
        lenient().when(budgetGuard.sign(any(), any())).thenReturn(BUDGET);
        // 纯单测无 MP 主键填充：插入桩补写 id，保证后续按 id 流转
        lenient().doAnswer(invocation -> {
            AiTask entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(TASK_ID);
            }
            return 1;
        }).when(taskMapper).insert(any(AiTask.class));
        lenient().doAnswer(invocation -> {
            AiTaskExecution entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(EXEC_ID);
            }
            return 1;
        }).when(executionMapper).insert(any(AiTaskExecution.class));
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    // ---------- 3.6.2 提交（创建即启动） ----------

    @Test
    void submit_waitSecondsInvalid_throws123() {
        AiTaskSubmitReqDTO req = submitReq(11);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.submit(req, PROJECT_ID, WORKSPACE_ID, USER_ID, loginUser));
        assertEquals(ErrorCodeConstants.AI_WAIT_SECONDS_INVALID.code(), exception.getCode());
        verifyNoInteractions(taskMapper, executionDispatcher);
    }

    @Test
    void submit_switchOff_throws101() {
        when(settingsReader.settings()).thenReturn(new AiSettingsReader.AiSettings(false, MODEL_ID, 600, 2));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.submit(submitReq(0), PROJECT_ID, WORKSPACE_ID, USER_ID, loginUser));
        assertEquals(ErrorCodeConstants.AI_DISABLED.code(), exception.getCode());
    }

    @Test
    void submit_unknownType_throws114() {
        when(adapterRegistry.get(TYPE)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.submit(submitReq(0), PROJECT_ID, WORKSPACE_ID, USER_ID, loginUser));
        assertEquals(ErrorCodeConstants.AI_TASK_TYPE_UNSUPPORTED.code(), exception.getCode());
    }

    @Test
    void submit_inputInvalid_throws115() {
        org.mockito.Mockito.doThrow(ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID))
                .when(adapter).validateInput(any(), any());

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.submit(submitReq(0), PROJECT_ID, WORKSPACE_ID, USER_ID, loginUser));
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), exception.getCode());
        verify(taskMapper, never()).insert(any(AiTask.class));
    }

    @Test
    void submit_budgetOverUpperBound_throws126() {
        when(budgetGuard.sign(any(), any()))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.AI_EXECUTION_BUDGET_INVALID));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.submit(submitReq(0), PROJECT_ID, WORKSPACE_ID, USER_ID, loginUser));
        assertEquals(ErrorCodeConstants.AI_EXECUTION_BUDGET_INVALID.code(), exception.getCode());
    }

    @Test
    void submit_success_createsTaskAndExecution_triggersRunner() {
        when(executionMapper.selectMaxSeqByTaskId(TASK_ID)).thenReturn(0);

        AiTaskRespDTO resp = service.submit(submitReq(0), PROJECT_ID, WORKSPACE_ID, USER_ID, loginUser);

        ArgumentCaptor<AiTask> taskCaptor = ArgumentCaptor.forClass(AiTask.class);
        verify(taskMapper).insert(taskCaptor.capture());
        AiTask created = taskCaptor.getValue();
        assertEquals(Constants.AiTaskFlowStatus.RUNNING, created.getStatus());
        assertEquals(TYPE, created.getType());
        assertEquals(PROJECT_ID, created.getProjectId());
        assertEquals(WORKSPACE_ID, created.getWorkspaceId());
        assertEquals(USER_ID, created.getSubmittedBy());

        ArgumentCaptor<AiTaskExecution> execCaptor = ArgumentCaptor.forClass(AiTaskExecution.class);
        verify(executionMapper).insert(execCaptor.capture());
        AiTaskExecution execution = execCaptor.getValue();
        assertEquals(TASK_ID, execution.getTaskId());
        assertEquals(1, execution.getSeq());
        assertEquals(Constants.AiTaskExecutionStatus.RUNNING, execution.getStatus());
        assertEquals(MODEL_ID, execution.getModelId());
        assertEquals(BUDGET, execution.getBudget());
        assertNotNull(execution.getHeartbeatAt());

        assertNotNull(resp.getExecutionId());
        assertEquals(BUDGET, resp.getBudget());
        assertEquals(Constants.AiTaskFlowStatus.RUNNING, resp.getStatus());
        // 创建即启动：无活动事务时直接触发执行核（@Async）
        verify(executionDispatcher).dispatch(any(TaskExecutionDispatcher.DispatchContext.class));
    }

    @Test
    void submit_activeTransaction_startsOnceAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.submit(submitReq(0), PROJECT_ID, WORKSPACE_ID, USER_ID, loginUser);

            verify(executionDispatcher, never()).dispatch(any());
            assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
            TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();
            verify(executionDispatcher).dispatch(any(TaskExecutionDispatcher.DispatchContext.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void submit_executionInsertFails_rollsBackAndDoesNotStart() {
        when(executionMapper.insert(any(AiTaskExecution.class)))
                .thenThrow(new IllegalStateException("insert failed"));

        assertThrows(IllegalStateException.class,
                () -> service.submit(submitReq(0), PROJECT_ID, WORKSPACE_ID, USER_ID, loginUser));

        verify(transactionManager).rollback(any());
        verify(executionDispatcher, never()).dispatch(any());
    }

    @Test
    void submit_capabilityVision_resolvesVisionModel() {
        when(adapter.requiredCapability(any(), any())).thenReturn("vision");
        when(settingsReader.resolveModel(null, "vision")).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.submit(submitReq(0), PROJECT_ID, WORKSPACE_ID, USER_ID, loginUser));
        assertEquals(ErrorCodeConstants.AI_VISION_MODEL_NOT_CONFIGURED.code(), exception.getCode());
    }

    @Test
    void submit_waitSeconds_terminalTask_returnsDetail() {
        when(executionMapper.selectMaxSeqByTaskId(TASK_ID)).thenReturn(0);
        AiTask running = task(Constants.AiTaskFlowStatus.RUNNING);
        AiTask done = task(Constants.AiTaskFlowStatus.PROPOSALS_READY);
        when(taskMapper.selectById(TASK_ID)).thenReturn(running, done);
        when(executionMapper.selectLatestByTaskId(TASK_ID)).thenReturn(execution("succeeded"));

        AiTaskRespDTO resp = service.submit(submitReq(1), PROJECT_ID, WORKSPACE_ID, USER_ID, loginUser);

        assertEquals(Constants.AiTaskFlowStatus.PROPOSALS_READY, resp.getStatus());
        assertEquals(EXEC_ID, resp.getLatestExecution().getExecutionId());
    }

    @Test
    void submitInternal_sameValidationPath_disabled_throws101() {
        when(settingsReader.settings()).thenReturn(new AiSettingsReader.AiSettings(false, MODEL_ID, 600, 2));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.submitInternal(TYPE, Map.of("fileId", "f-1"), USER_ID, PROJECT_ID, WORKSPACE_ID));
        assertEquals(ErrorCodeConstants.AI_DISABLED.code(), exception.getCode());
    }

    // ---------- 3.6.3 列表、计数与详情 ----------

    @Test
    void page_mapsListItems() {
        AiTaskPageReqDTO pageReq = new AiTaskPageReqDTO();
        when(taskMapper.findPage(pageReq, PROJECT_ID, USER_ID, null, null))
                .thenReturn(new PageResult<>(List.of(task(Constants.AiTaskFlowStatus.RUNNING)), 1L));
        when(executionMapper.selectLatestByTaskId(TASK_ID)).thenReturn(execution("running"));

        PageResult<AiTaskRespDTO> page = service.page(pageReq, PROJECT_ID, USER_ID);

        assertEquals(1, page.getTotal());
        assertEquals(TASK_ID, page.getList().get(0).getTaskId());
        assertEquals(EXEC_ID, page.getList().get(0).getLatestExecution().getExecutionId());
        // 列表项不下发 resultMeta / proposals / documentMeta（3.6.3）
        assertNull(page.getList().get(0).getProposals());
        assertNull(page.getList().get(0).getDocumentMeta());
    }

    @Test
    void countActive_returnsRunningCount() {
        when(taskMapper.countActive(PROJECT_ID, USER_ID)).thenReturn(3L);

        AiTaskCountRespDTO resp = service.countActive(PROJECT_ID, USER_ID, loginUser);
        assertEquals(Long.valueOf(3L), resp.getCount());
    }

    @Test
    void detail_notVisible_throws110() {
        AiTask others = task(Constants.AiTaskFlowStatus.RUNNING);
        others.setSubmittedBy(OTHER_ID);
        when(taskMapper.selectById(TASK_ID)).thenReturn(others);
        loginUser.setActiveProjectId(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.getDetail(TASK_ID, loginUser));
        assertEquals(ErrorCodeConstants.AI_TASK_NOT_FOUND.code(), exception.getCode());
    }

    @Test
    void detail_proposalsReady_returnsLatestExecutionProposals() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task(Constants.AiTaskFlowStatus.PROPOSALS_READY));
        when(executionMapper.selectLatestByTaskId(TASK_ID)).thenReturn(execution("succeeded"));
        when(proposalMapper.selectByExecution(EXEC_ID)).thenReturn(List.of(proposal("p1", "pending")));
        when(adoptionMapper.selectByExecution(EXEC_ID)).thenReturn(List.of(adoption("p1", "adopted")));

        AiTaskRespDTO resp = service.getDetail(TASK_ID, loginUser);

        assertEquals(1, resp.getProposals().size());
        assertEquals("p1", resp.getProposals().get(0).getKey());
        // 有采纳记录时按动作回填（3.6.3）
        assertEquals("adopted", resp.getProposals().get(0).getConfirmStatus());
        assertNotNull(resp.getLatestExecution());
    }

    @Test
    void detail_missingTask_throws110() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.getDetail(TASK_ID, loginUser));
        assertEquals(ErrorCodeConstants.AI_TASK_NOT_FOUND.code(), exception.getCode());
    }

    @Test
    void getProposal_returnsContentWithAdoptedStatus() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task(Constants.AiTaskFlowStatus.PROPOSALS_READY));
        when(executionMapper.selectLatestByTaskId(TASK_ID)).thenReturn(execution("succeeded"));
        AiTaskProposal proposal = proposal("p1", "pending");
        when(proposalMapper.selectByExecutionAndKey(EXEC_ID, "p1")).thenReturn(proposal);
        when(adoptionMapper.selectByExecutionAndKey(EXEC_ID, "p1")).thenReturn(adoption("p1", "adopted_edited"));

        AiTaskProposalRespDTO resp = service.getProposal(TASK_ID, "p1", loginUser);

        assertEquals("adopted_edited", resp.getConfirmStatus());
        assertEquals("T", resp.getTitle());
        assertNotNull(resp.getContent());
    }

    @Test
    void getProposal_missing_throws112() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task(Constants.AiTaskFlowStatus.PROPOSALS_READY));
        when(executionMapper.selectLatestByTaskId(TASK_ID)).thenReturn(execution("succeeded"));
        when(proposalMapper.selectByExecutionAndKey(EXEC_ID, "nope")).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.getProposal(TASK_ID, "nope", loginUser));
        assertEquals(ErrorCodeConstants.AI_PROPOSAL_NOT_FOUND.code(), exception.getCode());
    }

    // ---------- 3.6.4 轨迹、终止与再次执行 ----------

    @Test
    void events_cursorPaging_returnsNextSeq() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task(Constants.AiTaskFlowStatus.RUNNING));
        when(executionMapper.selectById(EXEC_ID)).thenReturn(execution("running"));
        AiTaskTraceEvent first = traceEvent(1);
        AiTaskTraceEvent second = traceEvent(2);
        when(traceEventMapper.selectAfterSeq(EXEC_ID, 0, 200)).thenReturn(List.of(first, second));

        AiTaskEventsRespDTO resp = service.getEvents(TASK_ID, EXEC_ID, 0, null, loginUser);

        assertEquals(2, resp.getItems().size());
        assertEquals(2, resp.getNextSeq());
    }

    @Test
    void events_executionOfAnotherTask_throws125() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task(Constants.AiTaskFlowStatus.RUNNING));
        when(executionMapper.selectById(EXEC_ID)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.getEvents(TASK_ID, EXEC_ID, null, null, loginUser));
        assertEquals(ErrorCodeConstants.AI_EXECUTION_NOT_FOUND.code(), exception.getCode());
    }

    @Test
    void abort_runningExecution_abortsExecutionAndTask() {
        when(taskMapper.selectById(TASK_ID))
                .thenReturn(task(Constants.AiTaskFlowStatus.RUNNING),
                        task(Constants.AiTaskFlowStatus.ABORTED));
        AiTaskExecution running = execution("running");
        when(executionMapper.selectById(EXEC_ID)).thenReturn(running);
        when(executionLifecycle.terminate(TASK_ID, EXEC_ID,
                ErrorCodeConstants.AI_TASK_ABORTED_BY_USER.code(), "用户中止")).thenReturn(true);

        AiTaskRespDTO resp = service.abort(TASK_ID, EXEC_ID, loginUser);

        verify(executionLifecycle).terminate(TASK_ID, EXEC_ID,
                ErrorCodeConstants.AI_TASK_ABORTED_BY_USER.code(), "用户中止");
        assertEquals(Constants.AiTaskFlowStatus.ABORTED, resp.getStatus());
    }

    @Test
    void abort_alreadyFinished_throws127() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task(Constants.AiTaskFlowStatus.RUNNING));
        when(executionMapper.selectById(EXEC_ID)).thenReturn(execution("succeeded"));
        when(executionLifecycle.terminate(TASK_ID, EXEC_ID,
                ErrorCodeConstants.AI_TASK_ABORTED_BY_USER.code(), "用户中止")).thenReturn(false);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.abort(TASK_ID, EXEC_ID, loginUser));
        assertEquals(ErrorCodeConstants.AI_EXECUTION_FINISHED.code(), exception.getCode());
        verify(executionLifecycle).terminate(TASK_ID, EXEC_ID,
                ErrorCodeConstants.AI_TASK_ABORTED_BY_USER.code(), "用户中止");
    }

    @Test
    void reExecute_wrongStatus_throws111() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task(Constants.AiTaskFlowStatus.RUNNING));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.reExecute(TASK_ID, new AiTaskExecutionCreateReqDTO(), loginUser));
        assertEquals(ErrorCodeConstants.AI_TASK_STATE_INVALID.code(), exception.getCode());
        verify(executionMapper, never()).insert(any(AiTaskExecution.class));
    }

    @Test
    void reExecute_proposalsReady_createsNextExecution() {
        AiTask ready = task(Constants.AiTaskFlowStatus.PROPOSALS_READY);
        AiTask running = task(Constants.AiTaskFlowStatus.RUNNING);
        when(taskMapper.selectById(TASK_ID)).thenReturn(ready, running);
        when(executionMapper.countActiveByTaskId(TASK_ID)).thenReturn(0L);
        AiTaskExecution previous = execution("succeeded");
        previous.setId(PREV_EXEC_ID);
        when(executionMapper.selectLatestByTaskId(TASK_ID)).thenReturn(previous);
        when(taskMapper.selectByIdForUpdate(TASK_ID)).thenReturn(ready);
        when(executionMapper.selectLatestByTaskIdForUpdate(TASK_ID)).thenReturn(previous);
        when(settingsReader.resolveModel(MODEL_ID, null)).thenReturn(model());
        when(taskMapper.markRerun(TASK_ID)).thenReturn(true);
        when(executionMapper.selectMaxSeqByTaskId(TASK_ID)).thenReturn(1);

        AiTaskExecutionCreateReqDTO req = new AiTaskExecutionCreateReqDTO();
        req.setFeedback("粒度细化到场景级");
        AiTaskRespDTO resp = service.reExecute(TASK_ID, req, loginUser);

        ArgumentCaptor<AiTaskExecution> captor = ArgumentCaptor.forClass(AiTaskExecution.class);
        verify(executionMapper).insert(captor.capture());
        AiTaskExecution created = captor.getValue();
        assertEquals(2, created.getSeq());
        assertEquals(PREV_EXEC_ID, created.getRetryOfExecutionId());
        assertEquals(MODEL_ID, created.getModelId());
        assertEquals(BUDGET, created.getBudget());
        assertNotNull(resp.getExecutionId());
        verify(taskMapper).markRerun(TASK_ID);
        verify(executionDispatcher).dispatch(any(TaskExecutionDispatcher.DispatchContext.class));
    }

    @Test
    void reExecute_executionInsertFails_rollsBackRerunAndDoesNotStart() {
        AiTask ready = task(Constants.AiTaskFlowStatus.PROPOSALS_READY);
        AiTaskExecution previous = execution("succeeded");
        previous.setId(PREV_EXEC_ID);
        when(taskMapper.selectById(TASK_ID)).thenReturn(ready);
        when(executionMapper.selectLatestByTaskId(TASK_ID)).thenReturn(previous);
        when(settingsReader.resolveModel(MODEL_ID, null)).thenReturn(model());
        when(taskMapper.selectByIdForUpdate(TASK_ID)).thenReturn(ready);
        when(executionMapper.selectLatestByTaskIdForUpdate(TASK_ID)).thenReturn(previous);
        when(executionMapper.countActiveByTaskId(TASK_ID)).thenReturn(0L);
        when(taskMapper.markRerun(TASK_ID)).thenReturn(true);
        when(executionMapper.insert(any(AiTaskExecution.class)))
                .thenThrow(new IllegalStateException("insert failed"));

        assertThrows(IllegalStateException.class,
                () -> service.reExecute(TASK_ID, null, loginUser));

        verify(transactionManager).rollback(any());
        verify(executionDispatcher, never()).dispatch(any());
    }

    @Test
    void reExecute_activeExecutionExists_throws111() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task(Constants.AiTaskFlowStatus.ABORTED));
        when(executionMapper.countActiveByTaskId(TASK_ID)).thenReturn(1L);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.reExecute(TASK_ID, null, loginUser));
        assertEquals(ErrorCodeConstants.AI_TASK_STATE_INVALID.code(), exception.getCode());
    }

    // ---------- 3.6.5 提案确认（逐项事务） ----------

    private AiTaskConfirmReqDTO confirmReq(String key, String action) {
        AiTaskConfirmReqDTO req = new AiTaskConfirmReqDTO();
        AiTaskConfirmReqDTO.ConfirmItemReqDTO item = new AiTaskConfirmReqDTO.ConfirmItemReqDTO();
        item.setKey(key);
        item.setAction(action);
        req.setItems(List.of(item));
        return req;
    }

    private void stubProposalsReadyWith(AiTaskProposal proposal) {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task(Constants.AiTaskFlowStatus.PROPOSALS_READY));
        when(executionMapper.selectLatestByTaskId(TASK_ID)).thenReturn(execution("succeeded"));
        when(proposalMapper.selectByExecution(EXEC_ID))
                .thenReturn(proposal == null ? List.of() : List.of(proposal));
    }

    @Test
    void confirm_taskNotProposalsReady_throws111() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task(Constants.AiTaskFlowStatus.RUNNING));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.confirm(TASK_ID, confirmReq("p1", "adopted"), loginUser));
        assertEquals(ErrorCodeConstants.AI_TASK_STATE_INVALID.code(), exception.getCode());
    }

    @Test
    void confirm_adopted_insertsAdoptionRecord() {
        stubProposalsReadyWith(proposal("p1", "pending"));
        when(adoptionMapper.selectByExecutionAndKey(EXEC_ID, "p1")).thenReturn(null);
        ProposalAdopter adopter = mock(ProposalAdopter.class);
        when(adopterRegistry.get(TYPE)).thenReturn(adopter);
        UUID createdId = UUID.randomUUID();
        when(adopter.adopt(any())).thenReturn(new AdoptOutcome(createdId, Map.of("id", createdId)));

        AiTaskConfirmRespDTO resp = service.confirm(TASK_ID, confirmReq("p1", "adopted"), loginUser);

        assertTrue(resp.getResults().get(0).getSuccess());
        assertEquals(createdId, resp.getResults().get(0).getCreatedId());
        ArgumentCaptor<AiTaskAdoption> captor = ArgumentCaptor.forClass(AiTaskAdoption.class);
        verify(adoptionMapper).insert(captor.capture());
        AiTaskAdoption record = captor.getValue();
        assertEquals(TASK_ID, record.getTaskId());
        assertEquals(EXEC_ID, record.getExecutionId());
        assertEquals("p1", record.getProposalKey());
        assertEquals("adopted", record.getAction());
        assertEquals(PROJECT_ID, record.getProjectId());
        assertEquals(USER_ID, record.getOperatorId());
    }

    @Test
    void confirm_proposalMissing_failsItemWith112() {
        stubProposalsReadyWith(null);

        AiTaskConfirmRespDTO resp = service.confirm(TASK_ID, confirmReq("ghost", "adopted"), loginUser);

        assertTrue(!resp.getResults().get(0).getSuccess());
        assertEquals(ErrorCodeConstants.AI_PROPOSAL_NOT_FOUND.code(), resp.getResults().get(0).getErrorCode());
        verify(adoptionMapper, never()).insert(any(AiTaskAdoption.class));
    }

    @Test
    void confirm_duplicateAdoption_failsItemWith113() {
        stubProposalsReadyWith(proposal("p1", "pending"));
        when(adoptionMapper.selectByExecutionAndKey(EXEC_ID, "p1")).thenReturn(null);
        ProposalAdopter adopter = mock(ProposalAdopter.class);
        when(adopterRegistry.get(TYPE)).thenReturn(adopter);
        org.mockito.Mockito.doThrow(new DuplicateKeyException("uk")).when(adoptionMapper)
                .insert(any(AiTaskAdoption.class));

        AiTaskConfirmRespDTO resp = service.confirm(TASK_ID, confirmReq("p1", "adopted"), loginUser);

        assertTrue(!resp.getResults().get(0).getSuccess());
        assertEquals(ErrorCodeConstants.AI_PROPOSAL_ALREADY_CONFIRMED.code(),
                resp.getResults().get(0).getErrorCode());
    }

    @Test
    void confirm_adopterThrows_failsItemWithItsCode() {
        stubProposalsReadyWith(proposal("p1", "adopted"));
        when(adoptionMapper.selectByExecutionAndKey(EXEC_ID, "p1")).thenReturn(null);
        ProposalAdopter adopter = mock(ProposalAdopter.class);
        when(adopterRegistry.get(TYPE)).thenReturn(adopter);
        when(adopter.adopt(any()))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_TYPE_UNSUPPORTED));

        AiTaskConfirmRespDTO resp = service.confirm(TASK_ID, confirmReq("p1", "adopted"), loginUser);

        assertTrue(!resp.getResults().get(0).getSuccess());
        assertEquals(ErrorCodeConstants.AI_TASK_TYPE_UNSUPPORTED.code(), resp.getResults().get(0).getErrorCode());
    }

    @Test
    void confirm_allDisposed_marksTaskClosed() {
        stubProposalsReadyWith(proposal("p1", "pending"));
        when(adoptionMapper.selectByExecutionAndKey(EXEC_ID, "p1")).thenReturn(null);
        ProposalAdopter adopter = mock(ProposalAdopter.class);
        when(adopterRegistry.get(TYPE)).thenReturn(adopter);
        when(adopter.adopt(any())).thenReturn(null);
        when(adoptionMapper.countByExecution(EXEC_ID)).thenReturn(1L);
        when(taskMapper.markFlowStatus(TASK_ID, Constants.AiTaskFlowStatus.CLOSED)).thenReturn(true);

        AiTaskConfirmRespDTO resp = service.confirm(TASK_ID, confirmReq("p1", "rejected"), loginUser);

        assertTrue(resp.getResults().get(0).getSuccess());
        verify(taskMapper).markFlowStatus(TASK_ID, Constants.AiTaskFlowStatus.CLOSED);
    }

    @Test
    void confirm_invalidAction_failsItemWith115() {
        stubProposalsReadyWith(proposal("p1", "pending"));

        AiTaskConfirmRespDTO resp = service.confirm(TASK_ID, confirmReq("p1", "destroy"), loginUser);

        assertTrue(!resp.getResults().get(0).getSuccess());
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), resp.getResults().get(0).getErrorCode());
    }

    // ---------- 跨层查询入口 ----------

    @Test
    void isTaskRunning_runningTrue_othersFalse() {
        when(taskMapper.selectById(TASK_ID))
                .thenReturn(task(Constants.AiTaskFlowStatus.RUNNING))
                .thenReturn(task(Constants.AiTaskFlowStatus.CLOSED));

        assertTrue(service.isTaskRunning(TASK_ID));
        assertTrue(!service.isTaskRunning(TASK_ID));
        assertTrue(!service.isTaskRunning(null));
    }

    // ---------- 夹具 ----------

    private static AiModelConfig model() {
        AiModelConfig model = new AiModelConfig();
        model.setId(MODEL_ID);
        model.setEnabled(true);
        model.setModelName("gpt-x");
        model.setBaseUrl("http://localhost:11434/v1");
        return model;
    }

    private static AiTaskSubmitReqDTO submitReq(Integer waitSeconds) {
        AiTaskSubmitReqDTO req = new AiTaskSubmitReqDTO();
        req.setType(TYPE);
        req.setInput(Map.of("fileId", "f-1"));
        req.setWaitSeconds(waitSeconds);
        return req;
    }

    private static AiTask task(String status) {
        AiTask task = new AiTask();
        task.setId(TASK_ID);
        task.setType(TYPE);
        task.setStatus(status);
        task.setProjectId(PROJECT_ID);
        task.setWorkspaceId(WORKSPACE_ID);
        task.setSubmittedBy(USER_ID);
        task.setInput(Map.of("fileId", "f-1"));
        task.setTokensIn(10);
        task.setTokensOut(20);
        task.setCreatedAt(LocalDateTime.now());
        return task;
    }

    private static AiTaskExecution execution(String status) {
        AiTaskExecution execution = new AiTaskExecution();
        execution.setId(EXEC_ID);
        execution.setTaskId(TASK_ID);
        execution.setSeq(1);
        execution.setStatus(status);
        execution.setModelId(MODEL_ID);
        execution.setBudget(BUDGET);
        execution.setSteps(3);
        execution.setTokensIn(10);
        execution.setTokensOut(20);
        execution.setCost(new BigDecimal("0.21"));
        execution.setCreatedAt(LocalDateTime.now().minusMinutes(2));
        return execution;
    }

    private static AiTaskProposal proposal(String key, String confirmStatus) {
        AiTaskProposal proposal = new AiTaskProposal();
        proposal.setTaskId(TASK_ID);
        proposal.setExecutionId(EXEC_ID);
        proposal.setProposalKey(key);
        proposal.setKind("test_case_suggestion");
        proposal.setTitle("T");
        proposal.setContent(Map.of("cells", List.of()));
        proposal.setConfirmStatus(confirmStatus);
        return proposal;
    }

    private static AiTaskAdoption adoption(String key, String action) {
        AiTaskAdoption adoption = new AiTaskAdoption();
        adoption.setTaskId(TASK_ID);
        adoption.setExecutionId(EXEC_ID);
        adoption.setProposalKey(key);
        adoption.setAction(action);
        return adoption;
    }

    private static AiTaskTraceEvent traceEvent(int seq) {
        AiTaskTraceEvent event = new AiTaskTraceEvent();
        event.setExecutionId(EXEC_ID);
        event.setSeq(seq);
        event.setEventType(Constants.AiTraceEventType.TOOL_CALL);
        event.setSummary("调用工具");
        event.setCreatedAt(LocalDateTime.now());
        return event;
    }
}
