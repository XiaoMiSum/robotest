package io.github.xiaomisum.robotest.service.ai.task;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTaskConfirmReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTaskExecutionCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTaskPageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTaskSubmitReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskConfirmRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskConfirmRespDTO.ItemResult;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskCountRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskEventsRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskExecutionRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskProposalRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskProposalSummaryRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskTraceEventRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTerminateReasonRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskAdoption;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskExecution;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskProposal;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskTraceEvent;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
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
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.migoo.framework.common.exception.ErrorCode;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.security.core.annotation.AuditLog;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 统一任务资源实现（总册 3.6，任务 · 执行 · 提案三资源）。
 *
 * <p>校验顺序遵循 3.6.2：总开关 → 类型（114）→ 输入（115）→ 预算（126）→ 可用模型（118 / 124）；
 * 任务端点不做权限校验（ai:task / ai:confirm 与任务类型资源权限均不校验，仅保留任务归属可见性），
 * 故不使用 @PreAuthorize。上下文（projectId / workspaceId）仅取自请求头解析后的 LoginUser（C4），
 * 不进 URL 与请求体。</p>
 */
@Service
public class AiTaskServiceImpl implements AiTaskService {

    private static final Logger log = LoggerFactory.getLogger(AiTaskServiceImpl.class);

    private static final int WAIT_SECONDS_MAX = 10;
    private static final long POLL_INTERVAL_MILLIS = 200;
    private static final int EVENTS_PAGE_DEFAULT = 200;
    private static final int EVENTS_PAGE_MAX = 500;
    private static final String ABORT_MSG = "用户中止";

    /** 逐项回执兜底：采纳落库的意外异常按请求级「失败仅该项回滚」语义回执，框架全局码 0–999 豁免（C3） */
    private static final ErrorCode ITEM_FAILED = ErrorCode.of(500, "采纳落库失败");

    private static final Set<String> CONFIRM_ACTIONS = Set.of(
            Constants.AiProposalAction.ADOPTED,
            Constants.AiProposalAction.ADOPTED_EDITED,
            Constants.AiProposalAction.REJECTED);

    @Resource
    private AiTaskMapper taskMapper;
    @Resource
    private AiTaskExecutionMapper executionMapper;
    @Resource
    private AiTaskProposalMapper proposalMapper;
    @Resource
    private AiTaskAdoptionMapper adoptionMapper;
    @Resource
    private AiTaskTraceEventMapper traceEventMapper;
    @Resource
    private AiSettingsReader settingsReader;
    @Resource
    private TaskTypeAdapterRegistry adapterRegistry;
    @Resource
    private BudgetGuard budgetGuard;
    @Resource
    private ProposalAdopterRegistry adopterRegistry;
    @Resource
    private TaskExecutionDispatcher executionDispatcher;
    @Resource
    private TraceRecorder traceRecorder;
    @Resource
    private ExecutionLifecycle executionLifecycle;
    @Resource
    private PlatformTransactionManager transactionManager;
    @Resource
    private RequirementMapper requirementMapper;

    // ---------- 3.6.2 提交（创建即启动） ----------

    @Override
    @AuditLog(action = "SUBMIT:AiTask")
    public AiTaskRespDTO submit(AiTaskSubmitReqDTO reqDTO, UUID activeProjectId, UUID activeWorkspaceId,
            UUID userId, LoginUser loginUser) {
        int waitSeconds = reqDTO.getWaitSeconds() == null ? 0 : reqDTO.getWaitSeconds();
        if (waitSeconds < 0 || waitSeconds > WAIT_SECONDS_MAX) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_WAIT_SECONDS_INVALID);
        }
        if (!settingsReader.settings().enabled()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_DISABLED);
        }
        return createTask(reqDTO.getType(), reqDTO.getInput(), reqDTO.getBudget(), waitSeconds,
                userId, activeProjectId, activeWorkspaceId, loginUser);
    }

    @Override
    public AiTaskRespDTO submitInternal(String type, Map<String, Object> input, UUID submittedBy, UUID projectId,
            UUID workspaceId) {
        if (!settingsReader.settings().enabled()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_DISABLED);
        }
        // 系统触发（需求导入 / 助手解析等）无登录态：类型 / 输入 / 预算 / 模型校验照常
        return createTask(type, input, null, 0, submittedBy, projectId, workspaceId, null);
    }

    private AiTaskRespDTO createTask(String type, Map<String, Object> input, Map<String, Object> budgetOverride,
            int waitSeconds, UUID userId, UUID projectId, UUID workspaceId, LoginUser loginUser) {
        TaskTypeAdapter adapter = adapterRegistry.get(type);
        if (adapter == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_TYPE_UNSUPPORTED);
        }
        Map<String, Object> effectiveInput = input == null ? Map.of() : new LinkedHashMap<>(input);
        TaskSubmitContext submitContext = new TaskSubmitContext(projectId, workspaceId, userId, loginUser);
        adapter.validateInput(effectiveInput, submitContext);
        // 预算签发（3.6.2）：类型默认为上限，请求仅可下调，越界 1000018126
        Map<String, Object> budget = budgetGuard.sign(adapter.defaultBudget(), budgetOverride);
        // 必需能力选型（总册 4.2）：适配器从输入派生（批量随附截图需结合项目范围）；
        // vector_reindex 豁免 chat 模型选型（4.2）：不解析模型、不报 1000018118/124，执行行 model_id 留空
        AiModelConfig model = null;
        if (!Constants.AiTaskType.VECTOR_REINDEX.equals(type)) {
            String requiredCapability = adapter.requiredCapability(effectiveInput, submitContext);
            model = requiredCapability == null
                    ? settingsReader.usableModel()
                    : settingsReader.resolveModel(null, requiredCapability);
            if (model == null) {
                throw ServiceExceptionUtil.get(requiredCapability == null
                        ? ErrorCodeConstants.AI_MODEL_NOT_CONFIGURED
                        : ErrorCodeConstants.AI_VISION_MODEL_NOT_CONFIGURED);
            }
        }
        AiModelConfig chatModel = model;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        CreatedRun created = template.execute(status -> {
            AiTask task = new AiTask();
            task.setType(type);
            task.setStatus(Constants.AiTaskFlowStatus.RUNNING);
            task.setProjectId(projectId);
            task.setWorkspaceId(workspaceId);
            task.setSubmittedBy(userId);
            task.setInput(effectiveInput);
            task.setTokensIn(0);
            task.setTokensOut(0);
            taskMapper.insert(task);
            AiTaskExecution execution = createExecution(task.getId(), userId, type,
                    chatModel == null ? null : chatModel.getId(), budget, null);
            triggerStart(task, execution, chatModel, workspaceId, projectId, null);
            return new CreatedRun(task, execution);
        });
        if (created == null) {
            throw new IllegalStateException("任务创建事务未返回结果");
        }
        AiTask task = created.task();
        AiTaskExecution execution = created.execution();
        if (waitSeconds > 0) {
            AiTask finished = await(task.getId(), waitSeconds);
            if (finished != null) {
                return buildDetailResp(finished, loginUser);
            }
        }
        return buildSubmitResp(task, execution);
    }

    /** 执行行创建（总册 2.6.2）：seq 同任务自增，预算快照固化，心跳置当前时间 */
    private AiTaskExecution createExecution(UUID taskId, UUID startedBy, String promptScene, UUID modelId,
            Map<String, Object> budget, UUID retryOfExecutionId) {
        AiTaskExecution execution = new AiTaskExecution();
        execution.setTaskId(taskId);
        execution.setSeq(executionMapper.selectMaxSeqByTaskId(taskId) + 1);
        execution.setStatus(Constants.AiTaskExecutionStatus.RUNNING);
        execution.setStartedBy(startedBy);
        execution.setPromptScene(promptScene);
        execution.setModelId(modelId);
        execution.setBudget(budget);
        execution.setSteps(0);
        execution.setTokensIn(0);
        execution.setTokensOut(0);
        execution.setCost(BigDecimal.ZERO);
        execution.setRetryOfExecutionId(retryOfExecutionId);
        execution.setHeartbeatAt(LocalDateTime.now());
        executionMapper.insert(execution);
        return execution;
    }

    /**
     * 启动单入口（迁移计划层 A：收敛旧「直接 + 注册兜底」双触发）：
     * 活动事务内注册 afterCommit，提交后触发一次；无事务直接触发（执行核 @Async）。
     */
    private void triggerStart(AiTask task, AiTaskExecution execution, AiModelConfig model,
            UUID workspaceId, UUID projectId, String feedback) {
        TaskExecutionDispatcher.DispatchContext ctx = new TaskExecutionDispatcher.DispatchContext(
                task, execution, model, workspaceId, projectId, feedback);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executionDispatcher.dispatch(ctx);
                }
            });
        } else {
            executionDispatcher.dispatch(ctx);
        }
    }

    /**
     * 同步等待（3.6.2）：轮询任务态至终态（proposals_ready / closed / aborted），
     * 等待期内完成返回任务行，超时返回 null 由调用方回落执行中状态转轮询。
     */
    private AiTask await(UUID taskId, int waitSeconds) {
        long deadline = System.currentTimeMillis() + waitSeconds * 1000L;
        while (true) {
            AiTask latest = taskMapper.selectById(taskId);
            if (latest != null && isTaskTerminal(latest.getStatus())) {
                return latest;
            }
            if (System.currentTimeMillis() >= deadline) {
                return null;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    // ---------- 3.6.3 列表与详情 ----------

    @Override
    public PageResult<AiTaskRespDTO> page(AiTaskPageReqDTO pageReq, UUID activeProjectId, UUID userId) {
        PageResult<AiTask> page = taskMapper.findPage(pageReq, activeProjectId, userId,
                pageReq.getType(), pageReq.getStatus());
        return new PageResult<>(page.getList().stream()
                .map(this::buildListItem)
                .toList(), page.getTotal());
    }

    @Override
    public AiTaskCountRespDTO countActive(UUID activeProjectId, UUID userId, LoginUser loginUser) {
        AiTaskCountRespDTO resp = new AiTaskCountRespDTO();
        resp.setCount(taskMapper.countActive(activeProjectId, userId));
        return resp;
    }

    @Override
    public AiTaskRespDTO getDetail(UUID taskId, LoginUser loginUser) {
        return buildDetailResp(requireVisible(taskId, loginUser), loginUser);
    }

    @Override
    public AiTaskProposalRespDTO getProposal(UUID taskId, String proposalKey, LoginUser loginUser) {
        AiTask task = requireVisible(taskId, loginUser);
        AiTaskProposal proposal = locateProposal(task, proposalKey);
        AiTaskProposalRespDTO resp = new AiTaskProposalRespDTO();
        resp.setKey(proposal.getProposalKey());
        resp.setKind(proposal.getKind());
        resp.setTitle(proposal.getTitle());
        resp.setParentKey(proposal.getParentKey());
        resp.setConfirmStatus(resolveConfirmStatus(proposal));
        resp.setContent(proposal.getContent());
        // 生成链产物回读比对来源需求当前状态（生成链详设 4.1）：sourceRefs 逐条标注 changed
        markSourceChanged(task, proposal.getContent());
        return resp;
    }

    @Override
    public AiTaskEventsRespDTO getEvents(UUID taskId, UUID executionId, Integer afterSeq, Integer pageSize,
            LoginUser loginUser) {
        requireVisible(taskId, loginUser);
        requireExecution(taskId, executionId);
        int cursor = afterSeq == null || afterSeq < 0 ? 0 : afterSeq;
        int size = pageSize == null ? EVENTS_PAGE_DEFAULT
                : Math.min(Math.max(pageSize, 1), EVENTS_PAGE_MAX);
        List<AiTaskTraceEventRespDTO> items = traceEventMapper.selectAfterSeq(executionId, cursor, size)
                .stream()
                .map(this::toEventResp)
                .collect(Collectors.toList());
        AiTaskEventsRespDTO resp = new AiTaskEventsRespDTO();
        resp.setItems(items);
        resp.setNextSeq(items.isEmpty() ? cursor : items.get(items.size() - 1).getSeq());
        return resp;
    }

    // ---------- 3.6.4 终止与再次执行 ----------

    @Override
    @AuditLog(action = "ABORT:AiTask")
    public AiTaskRespDTO abort(UUID taskId, UUID executionId, LoginUser loginUser) {
        requireVisible(taskId, loginUser);
        requireExecution(taskId, executionId);
        if (!executionLifecycle.terminate(taskId, executionId,
                ErrorCodeConstants.AI_TASK_ABORTED_BY_USER.code(), ABORT_MSG)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_EXECUTION_FINISHED);
        }
        return buildDetailResp(taskMapper.selectById(taskId), loginUser);
    }

    @Override
    @AuditLog(action = "REEXECUTE:AiTask")
    public AiTaskRespDTO reExecute(UUID taskId, AiTaskExecutionCreateReqDTO reqDTO, LoginUser loginUser) {
        AiTask task = requireVisible(taskId, loginUser);
        // 门禁（3.6.4）：aborted（终止后重跑）或 proposals_ready（附反馈重新生成）
        if (!Constants.AiTaskFlowStatus.ABORTED.equals(task.getStatus())
                && !Constants.AiTaskFlowStatus.PROPOSALS_READY.equals(task.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_STATE_INVALID);
        }
        if (!settingsReader.settings().enabled()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_DISABLED);
        }
        TaskTypeAdapter adapter = adapterRegistry.get(task.getType());
        if (adapter == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_TYPE_UNSUPPORTED);
        }
        // 助手消息禁止经通用「再次执行」重跑（分册 04 §4.1）：重新发送生成新消息与新任务
        if (Constants.AiTaskType.ASSISTANT_PARSE.equals(task.getType())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_TYPE_UNSUPPORTED);
        }
        // 同任务同时至多一个进行中执行（3.6.4）
        if (executionMapper.countActiveByTaskId(taskId) > 0) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_STATE_INVALID);
        }
        AiTaskExecution previous = executionMapper.selectLatestByTaskId(taskId);
        String feedback = reqDTO == null || reqDTO.getFeedback() == null
                || reqDTO.getFeedback().isBlank() ? null : reqDTO.getFeedback().trim();
        TaskSubmitContext submitContext = new TaskSubmitContext(task.getProjectId(), task.getWorkspaceId(),
                loginUser.getId(), loginUser);
        // model_id 复用原执行模型（3.6.4），其被停用时按能力回落当前可用模型；
        // vector_reindex 豁免 chat 模型选型（总册 4.2），再次执行同样不解析模型
        AiModelConfig model = null;
        if (!Constants.AiTaskType.VECTOR_REINDEX.equals(task.getType())) {
            String requiredCapability = adapter.requiredCapability(task.getInput(), submitContext);
            model = settingsReader.resolveModel(
                    previous == null ? null : previous.getModelId(), requiredCapability);
            if (model == null) {
                throw ServiceExceptionUtil.get(requiredCapability == null
                        ? ErrorCodeConstants.AI_MODEL_NOT_CONFIGURED
                        : ErrorCodeConstants.AI_VISION_MODEL_NOT_CONFIGURED);
            }
        }
        Map<String, Object> budget = budgetGuard.sign(adapter.defaultBudget(), null);
        UUID expectedPreviousId = previous == null ? null : previous.getId();
        AiModelConfig chatModel = model;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        CreatedRun created = template.execute(status -> {
            AiTask lockedTask = taskMapper.selectByIdForUpdate(taskId);
            if (lockedTask == null || (!Constants.AiTaskFlowStatus.ABORTED.equals(lockedTask.getStatus())
                    && !Constants.AiTaskFlowStatus.PROPOSALS_READY.equals(lockedTask.getStatus()))) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_STATE_INVALID);
            }
            AiTaskExecution lockedPrevious = executionMapper.selectLatestByTaskIdForUpdate(taskId);
            UUID lockedPreviousId = lockedPrevious == null ? null : lockedPrevious.getId();
            if (!Objects.equals(expectedPreviousId, lockedPreviousId)
                    || executionMapper.countActiveByTaskId(taskId) > 0
                    || !taskMapper.markRerun(taskId)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_STATE_INVALID);
            }
            lockedTask.setStatus(Constants.AiTaskFlowStatus.RUNNING);
            lockedTask.setTerminateCode(null);
            lockedTask.setTerminateMsg(null);
            AiTaskExecution execution = createExecution(taskId, loginUser.getId(), task.getType(),
                    chatModel == null ? null : chatModel.getId(), budget, lockedPreviousId);
            triggerStart(lockedTask, execution, chatModel, task.getWorkspaceId(), task.getProjectId(), feedback);
            return new CreatedRun(lockedTask, execution);
        });
        if (created == null) {
            throw new IllegalStateException("再次执行事务未返回结果");
        }
        return buildSubmitResp(created.task(), created.execution());
    }

    // ---------- 3.6.5 提案确认（逐项事务） ----------

    @Override
    @AuditLog(action = "CONFIRM:AiProposal")
    public AiTaskConfirmRespDTO confirm(UUID taskId, AiTaskConfirmReqDTO reqDTO, LoginUser loginUser) {
        AiTask task = requireVisible(taskId, loginUser);
        // 确认门禁（3.6.5）：任务须 proposals_ready
        if (!Constants.AiTaskFlowStatus.PROPOSALS_READY.equals(task.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_STATE_INVALID);
        }
        AiTaskExecution latest = executionMapper.selectLatestByTaskId(taskId);
        if (latest == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_PROPOSAL_NOT_FOUND);
        }
        List<AiTaskProposal> proposals = proposalMapper.selectByExecution(latest.getId());
        Set<String> allKeys = proposals.stream()
                .map(AiTaskProposal::getProposalKey)
                .collect(Collectors.toSet());
        // 落库目标项目取自 X-Active-Project 头（C4），请求体只携带位置提示
        UUID projectId = loginUser.getActiveProjectId();
        UUID operatorId = loginUser.getId();
        List<ItemResult> results = new ArrayList<>();
        for (AiTaskConfirmReqDTO.ConfirmItemReqDTO item : reqDTO.getItems()) {
            results.add(confirmItem(task, latest.getId(), proposals, allKeys, item,
                    reqDTO.getTarget(), projectId, operatorId, loginUser));
        }
        markClosedIfAllDisposed(taskId, latest.getId(), proposals);
        AiTaskConfirmRespDTO resp = new AiTaskConfirmRespDTO();
        resp.setResults(results);
        return resp;
    }

    /**
     * 逐项确认（总册 3.6.5）：每项独立事务，采纳记录（ai_task_adoption）与业务落库同事务，
     * 失败仅该项回滚、其余项继续（部分失败，成功项保留）。
     */
    private ItemResult confirmItem(AiTask task, UUID executionId, List<AiTaskProposal> proposals,
            Set<String> allKeys, AiTaskConfirmReqDTO.ConfirmItemReqDTO item,
            AiTaskConfirmReqDTO.TargetReqDTO target, UUID projectId, UUID operatorId, LoginUser loginUser) {
        String action = item.getAction();
        try {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            return template.execute(status -> {
                AiTaskProposal proposal = proposals.stream()
                        .filter(candidate -> candidate.getProposalKey().equals(item.getKey()))
                        .findFirst()
                        .orElse(null);
                if (proposal == null) {
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_PROPOSAL_NOT_FOUND);
                }
                if (action == null || !CONFIRM_ACTIONS.contains(action)) {
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
                }
                if (adoptionMapper.selectByExecutionAndKey(executionId, item.getKey()) != null) {
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_PROPOSAL_ALREADY_CONFIRMED);
                }
                ProposalAdopter adopter = adopterRegistry.get(task.getType());
                if (adopter == null) {
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_TYPE_UNSUPPORTED);
                }
                AdoptOutcome outcome = adopter.adopt(new AdoptContext(task, proposal, action,
                        item.getContent(), item.getNote(),
                        target == null ? null : target.getModuleId(),
                        target == null ? null : target.getPosition(),
                        target == null ? null : target.getSystemVersion(),
                        target == null ? null : target.getCreateParams(),
                        target == null ? null : target.getDocumentId(),
                        target == null ? null : target.getExtraNodePosition(),
                        target == null ? null : target.getPlanId(),
                        target == null ? null : target.getRound(),
                        projectId, operatorId, allKeys, loginUser));
                AiTaskAdoption record = new AiTaskAdoption();
                record.setProjectId(projectId);
                record.setTaskId(task.getId());
                record.setExecutionId(executionId);
                record.setProposalKey(item.getKey());
                record.setAction(action);
                record.setOperatorId(operatorId);
                if (outcome != null) {
                    record.setAdoptedRef(outcome.adoptedRef());
                }
                record.setNote(item.getNote());
                // uk_ai_task_adoption 并发兜底：唯一冲突在本事务内转换为 1000018113
                adoptionMapper.insert(record);
                return ItemResult.ok(item.getKey(), action, outcome == null ? null : outcome.createdId());
            });
        } catch (ServiceException e) {
            return ItemResult.fail(item.getKey(), action, e.getCode(), e.getMessage());
        } catch (DuplicateKeyException e) {
            return ItemResult.fail(item.getKey(), action,
                    ErrorCodeConstants.AI_PROPOSAL_ALREADY_CONFIRMED.code(),
                    ErrorCodeConstants.AI_PROPOSAL_ALREADY_CONFIRMED.msg());
        } catch (Exception e) {
            log.warn("[AI] 提案确认逐项失败 taskId={} key={}", task.getId(), item.getKey(), e);
            return ItemResult.fail(item.getKey(), action, ITEM_FAILED.code(), ITEM_FAILED.msg());
        }
    }

    /** 全部待采纳提案处置完毕 → 任务转 closed（总册 3.6.5），条件写与并发确认竞争以先到者为准 */
    private void markClosedIfAllDisposed(UUID taskId, UUID executionId, List<AiTaskProposal> proposals) {
        long pending = proposals.stream()
                .filter(proposal -> Constants.AiProposalConfirmStatus.PENDING
                        .equals(proposal.getConfirmStatus()))
                .count();
        if (pending > 0 && adoptionMapper.countByExecution(executionId) >= pending
                && taskMapper.markFlowStatus(taskId, Constants.AiTaskFlowStatus.CLOSED)) {
            traceRecorder.record(executionId, Constants.AiTraceEventType.STATUS_CHANGE,
                    "任务关闭", Map.of("task", Constants.AiTaskFlowStatus.CLOSED));
        }
    }

    // ---------- 跨层查询入口（迁移计划层 A：禁止直读 ai_task） ----------

    @Override
    public boolean isTaskRunning(UUID taskId) {
        AiTask task = taskId == null ? null : taskMapper.selectById(taskId);
        return task != null && Constants.AiTaskFlowStatus.RUNNING.equals(task.getStatus());
    }

    // ---------- 组装 ----------

    /** 提交 / 再次执行响应（3.6.2）：executionId + 本次签发预算，其余场景字段为 null */
    private AiTaskRespDTO buildSubmitResp(AiTask task, AiTaskExecution execution) {
        AiTaskRespDTO resp = new AiTaskRespDTO();
        fillTaskBase(resp, task);
        resp.setExecutionId(execution.getId());
        resp.setBudget(execution.getBudget());
        resp.setResultMeta(task.getResultMeta());
        return resp;
    }

    /** 列表项（3.6.3）：latestExecution 摘要，不下发 resultMeta / proposals / documentMeta */
    private AiTaskRespDTO buildListItem(AiTask task) {
        AiTaskRespDTO resp = new AiTaskRespDTO();
        fillTaskBase(resp, task);
        resp.setLatestExecution(toExecutionResp(executionMapper.selectLatestByTaskId(task.getId())));
        return resp;
    }

    /** 详情（3.6.3）：最新一次执行 + 提案清单摘要（最新一次执行）+ documentMeta */
    private AiTaskRespDTO buildDetailResp(AiTask task, LoginUser loginUser) {
        AiTaskRespDTO resp = new AiTaskRespDTO();
        fillTaskBase(resp, task);
        AiTaskExecution latest = executionMapper.selectLatestByTaskId(task.getId());
        resp.setLatestExecution(toExecutionResp(latest));
        resp.setResultMeta(task.getResultMeta());
        resp.setDocumentMeta(documentMetaOf(task));
        if (latest != null && (Constants.AiTaskFlowStatus.PROPOSALS_READY.equals(task.getStatus())
                || Constants.AiTaskFlowStatus.CLOSED.equals(task.getStatus()))) {
            resp.setProposals(proposalSummaries(task.getId(), latest.getId()));
        }
        return resp;
    }

    private void fillTaskBase(AiTaskRespDTO resp, AiTask task) {
        resp.setTaskId(task.getId());
        resp.setType(task.getType());
        resp.setStatus(task.getStatus());
        resp.setSubmittedBy(task.getSubmittedBy());
        resp.setTokensIn(task.getTokensIn());
        resp.setTokensOut(task.getTokensOut());
        resp.setCreatedAt(task.getCreatedAt());
        resp.setTerminateReason(terminateReason(task.getTerminateCode(), task.getTerminateMsg()));
    }

    private AiTaskExecutionRespDTO toExecutionResp(AiTaskExecution execution) {
        if (execution == null) {
            return null;
        }
        AiTaskExecutionRespDTO resp = new AiTaskExecutionRespDTO();
        resp.setExecutionId(execution.getId());
        resp.setSeq(execution.getSeq());
        resp.setStatus(execution.getStatus());
        resp.setSteps(execution.getSteps());
        resp.setBudget(execution.getBudget());
        resp.setPlan(execution.getPlan());
        resp.setTerminateReason(terminateReason(execution.getTerminateCode(), execution.getTerminateMsg()));
        AiTaskExecutionRespDTO.Consumed consumed = new AiTaskExecutionRespDTO.Consumed();
        consumed.setDurationMs(execution.getDurationMs());
        consumed.setTokensIn(execution.getTokensIn());
        consumed.setTokensOut(execution.getTokensOut());
        consumed.setCost(execution.getCost());
        resp.setConsumed(consumed);
        return resp;
    }

    /** 提案清单摘要（3.6.3）：confirmStatus 由采纳记录回填动作，无记录取提案自身状态 */
    private List<AiTaskProposalSummaryRespDTO> proposalSummaries(UUID taskId, UUID executionId) {
        Map<String, String> adoptedActions = new LinkedHashMap<>();
        for (AiTaskAdoption record : adoptionMapper.selectByExecution(executionId)) {
            adoptedActions.putIfAbsent(record.getProposalKey(), record.getAction());
        }
        List<AiTaskProposalSummaryRespDTO> summaries = new ArrayList<>();
        for (AiTaskProposal proposal : proposalMapper.selectByExecution(executionId)) {
            AiTaskProposalSummaryRespDTO summary = new AiTaskProposalSummaryRespDTO();
            summary.setKey(proposal.getProposalKey());
            summary.setKind(proposal.getKind());
            summary.setTitle(proposal.getTitle());
            summary.setParentKey(proposal.getParentKey());
            summary.setConfirmStatus(adoptedActions.getOrDefault(proposal.getProposalKey(),
                    proposal.getConfirmStatus()));
            summaries.add(summary);
        }
        return summaries;
    }

    private String resolveConfirmStatus(AiTaskProposal proposal) {
        AiTaskAdoption adoption = adoptionMapper.selectByExecutionAndKey(
                proposal.getExecutionId(), proposal.getProposalKey());
        return adoption != null ? adoption.getAction() : proposal.getConfirmStatus();
    }

    /** result_meta 顶层文档级元数据（3.6.3 documentMeta，导入识别版本），无为 null */
    private AiTaskRespDTO.DocumentMeta documentMetaOf(AiTask task) {
        Map<String, Object> meta = task.getResultMeta();
        if (meta == null) {
            return null;
        }
        AiTaskRespDTO.DocumentMeta documentMeta = new AiTaskRespDTO.DocumentMeta();
        documentMeta.setDetectedVersion(asString(meta.get("detectedVersion")));
        documentMeta.setVersionEvidence(asString(meta.get("versionEvidence")));
        return documentMeta;
    }

    private AiTaskTraceEventRespDTO toEventResp(AiTaskTraceEvent event) {
        AiTaskTraceEventRespDTO resp = new AiTaskTraceEventRespDTO();
        resp.setSeq(event.getSeq());
        resp.setEventType(event.getEventType());
        resp.setSummary(event.getSummary());
        resp.setDetail(event.getDetail());
        resp.setCreatedAt(event.getCreatedAt());
        return resp;
    }

    private static AiTerminateReasonRespDTO terminateReason(Integer code, String msg) {
        return code == null ? null : new AiTerminateReasonRespDTO(code, msg);
    }

    /**
     * 生成链产物回读比对来源需求当前状态（生成链详设 4.1）：sourceRefs 逐条标注 changed
     * （需求已删除 / 离开已确认态 / 快照后更新时间变化），确认面板据此展示「来源需求已变更」。
     */
    @SuppressWarnings("unchecked")
    private void markSourceChanged(AiTask task, Map<String, Object> content) {
        if (task.getInput() == null
                || !(task.getInput().get("requirementSnapshots") instanceof List<?> snapshots)
                || content == null
                || !(content.get("sourceRefs") instanceof List<?> refs)) {
            return;
        }
        Map<String, Map<String, Object>> snapshotById = new LinkedHashMap<>();
        for (Object element : snapshots) {
            if (element instanceof Map<?, ?> snapshot && snapshot.get("id") != null) {
                snapshotById.put(String.valueOf(snapshot.get("id")), (Map<String, Object>) snapshot);
            }
        }
        Map<UUID, Requirement> currentById = new LinkedHashMap<>();
        List<UUID> requirementIds = new ArrayList<>();
        for (Object element : refs) {
            if (element instanceof Map<?, ?> ref && ref.get("requirementId") != null) {
                try {
                    requirementIds.add(UUID.fromString(String.valueOf(ref.get("requirementId"))));
                } catch (IllegalArgumentException ignored) {
                    // 非法引用无法比对，保持未标注
                }
            }
        }
        if (!requirementIds.isEmpty()) {
            requirementMapper.listByIds(requirementIds)
                    .forEach(item -> currentById.put(item.getId(), item));
        }
        for (Object element : refs) {
            if (!(element instanceof Map)) {
                continue;
            }
            Map<String, Object> ref = (Map<String, Object>) element;
            UUID id = parseUuid(ref.get("requirementId"));
            Requirement current = id == null ? null : currentById.get(id);
            ref.put("changed", sourceChanged(snapshotById.get(String.valueOf(ref.get("requirementId"))), current));
        }
    }

    private static boolean sourceChanged(Map<String, Object> snapshot, Requirement current) {
        if (current == null) {
            // 快照存在但需求已删除 → 已变更；两侧皆无从判断时不标注
            return snapshot != null;
        }
        if (!Constants.RequirementStatus.CONFIRMED.equals(current.getStatus())) {
            return true;
        }
        if (snapshot == null || snapshot.get("updatedAt") == null || current.getUpdatedAt() == null) {
            return false;
        }
        return !String.valueOf(snapshot.get("updatedAt")).equals(String.valueOf(current.getUpdatedAt()));
    }

    // ---------- 定位与校验 ----------

    /**
     * 可见性（总册 3.6.3）：带项目上下文看本项目任务，否则只看本人提交的任务；越权按不存在处理。
     */
    private AiTask requireVisible(UUID taskId, LoginUser loginUser) {
        AiTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_NOT_FOUND);
        }
        UUID activeProjectId = loginUser.getActiveProjectId();
        boolean visible = Objects.equals(task.getSubmittedBy(), loginUser.getId())
                || (activeProjectId != null && activeProjectId.equals(task.getProjectId()));
        if (!visible) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_NOT_FOUND);
        }
        return task;
    }

    /** 执行定位（3.6.4 / 3.6.5）：不存在或不属该任务均按 1000018125 */
    private AiTaskExecution requireExecution(UUID taskId, UUID executionId) {
        AiTaskExecution execution = executionId == null ? null : executionMapper.selectById(executionId);
        if (execution == null || !taskId.equals(execution.getTaskId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_EXECUTION_NOT_FOUND);
        }
        return execution;
    }

    /** 提案定位（3.6.3 / 3.6.5）：最新一次执行内按键查找，缺失按 1000018112 */
    private AiTaskProposal locateProposal(AiTask task, String proposalKey) {
        AiTaskExecution latest = executionMapper.selectLatestByTaskId(task.getId());
        AiTaskProposal proposal = latest == null || proposalKey == null ? null
                : proposalMapper.selectByExecutionAndKey(latest.getId(), proposalKey);
        if (proposal == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_PROPOSAL_NOT_FOUND);
        }
        return proposal;
    }

    private static boolean isTaskTerminal(String status) {
        return Constants.AiTaskFlowStatus.PROPOSALS_READY.equals(status)
                || Constants.AiTaskFlowStatus.CLOSED.equals(status)
                || Constants.AiTaskFlowStatus.ABORTED.equals(status);
    }

    private static UUID parseUuid(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(raw));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record CreatedRun(AiTask task, AiTaskExecution execution) {
    }
}
