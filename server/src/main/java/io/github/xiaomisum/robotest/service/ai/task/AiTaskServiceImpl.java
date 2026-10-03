package io.github.xiaomisum.robotest.service.ai.task;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTaskConfirmReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTaskPageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTaskSubmitReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskConfirmRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskConfirmRespDTO.ItemResult;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiArtifactConfirm;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.repository.ai.AiArtifactConfirmMapper;
import io.github.xiaomisum.robotest.repository.ai.AiTaskMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiSettingsReader;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.exception.ErrorCode;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.security.core.annotation.AuditLog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 统一任务资源实现（详设 3.6）。
 *
 * <p>校验顺序遵循 3.6.2：总开关 → 权限（1000018116）→ 类型（114）→ 输入（115）→ 可用模型（118）；
 * 因总开关须先于权限判定，任务端点不使用 @PreAuthorize（框架 403 会短路设计顺序），权限在服务层校验。
 * 上下文（projectId / workspaceId）仅取自请求头解析后的 LoginUser（C4），不进 URL 与请求体。</p>
 */
@Service
public class AiTaskServiceImpl implements AiTaskService {

    private static final Logger log = LoggerFactory.getLogger(AiTaskServiceImpl.class);

    private static final String PERMISSION_AI_TASK = "ai:task";
    private static final String PERMISSION_AI_CONFIRM = "ai:confirm";
    private static final int WAIT_SECONDS_MAX = 10;
    private static final long POLL_INTERVAL_MILLIS = 200;

    /** 逐项回执兜底：采纳落库的意外异常按请求级「失败仅该项回滚」语义回执，框架全局码 0–999 豁免（C3） */
    private static final ErrorCode ITEM_FAILED = ErrorCode.of(500, "采纳落库失败");

    private static final Set<String> CONFIRM_ACTIONS = Set.of(
            Constants.AiArtifactAction.ADOPTED,
            Constants.AiArtifactAction.ADOPTED_EDITED,
            Constants.AiArtifactAction.REJECTED);

    @Resource
    private AiTaskMapper taskMapper;
    @Resource
    private AiArtifactConfirmMapper confirmMapper;
    @Resource
    private AiSettingsReader settingsReader;
    @Resource
    private TaskHandlerRegistry handlerRegistry;
    @Resource
    private ArtifactAdopterRegistry adopterRegistry;
    @Resource
    private AiTaskExecutor taskExecutor;
    @Resource
    private PlatformTransactionManager transactionManager;

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
        if (!hasPermission(loginUser, PERMISSION_AI_TASK)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_NO_PERMISSION);
        }
        return createTask(reqDTO.getType(), reqDTO.getInput(), waitSeconds,
                userId, activeProjectId, activeWorkspaceId, loginUser);
    }

    @Override
    public AiTaskRespDTO submitInternal(String type, Map<String, Object> input, UUID submittedBy, UUID projectId,
            UUID workspaceId) {
        if (!settingsReader.settings().enabled()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_DISABLED);
        }
        // 系统触发（影响分析等）无登录态：跳过权限校验，类型 / 输入 / 模型校验照常
        return createTask(type, input, 0, submittedBy, projectId, workspaceId, null);
    }

    private AiTaskRespDTO createTask(String type, Map<String, Object> input, int waitSeconds,
            UUID userId, UUID projectId, UUID workspaceId, LoginUser loginUser) {
        TaskHandler handler = handlerRegistry.get(type);
        if (handler == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_TYPE_UNSUPPORTED);
        }
        Map<String, Object> effectiveInput = input == null ? Map.of() : new LinkedHashMap<>(input);
        handler.validateInput(effectiveInput);
        if (loginUser != null) {
            handler.checkPermission(loginUser);
        }
        AiModelConfig model = settingsReader.usableModel();
        if (model == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_NOT_CONFIGURED);
        }
        AiTask task = new AiTask();
        task.setType(type);
        task.setStatus(Constants.AiTaskStatus.PENDING);
        task.setProgress(0);
        task.setProjectId(projectId);
        task.setWorkspaceId(workspaceId);
        task.setSubmittedBy(userId);
        // prompt_scene / model_id 提交时回写（4.1），用量可归因、执行不随默认模型漂移
        task.setPromptScene(type);
        task.setModelId(model.getId());
        task.setInput(effectiveInput);
        task.setTokensIn(0);
        task.setTokensOut(0);
        taskMapper.insert(task);
        startTask(task.getId());
        AiTask fresh = waitSeconds > 0 ? await(task.getId(), waitSeconds) : task;
        if (fresh == null) {
            fresh = task;
        }
        return buildResp(fresh, true, waitSeconds > 0);
    }

    @Override
    public PageResult<AiTaskRespDTO> page(AiTaskPageReqDTO pageReq, UUID activeProjectId, UUID userId) {
        PageResult<AiTask> page = taskMapper.findPage(pageReq, activeProjectId, userId,
                pageReq.getType(), pageReq.getStatus());
        return new PageResult<>(page.getList().stream()
                .map(task -> buildResp(task, false, false))
                .toList(), page.getTotal());
    }

    @Override
    public AiTaskRespDTO getDetail(UUID taskId, LoginUser loginUser) {
        return buildResp(requireVisible(taskId, loginUser), true, false);
    }

    @Override
    public Map<String, Object> getArtifact(UUID taskId, String artifactKey, LoginUser loginUser) {
        AiTask task = requireVisible(taskId, loginUser);
        Map<String, Object> artifact = findArtifact(task, artifactKey);
        if (artifact == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_ARTIFACT_NOT_FOUND);
        }
        return artifact;
    }

    @Override
    @AuditLog(action = "CANCEL:AiTask")
    public AiTaskRespDTO cancel(UUID taskId, LoginUser loginUser) {
        AiTask task = requireVisible(taskId, loginUser);
        if (!hasPermission(loginUser, PERMISSION_AI_TASK)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_NO_PERMISSION);
        }
        if (!taskMapper.casCancel(taskId)) {
            // 仅 pending / running 可取消；条件写与执行线程竞争，失败即状态不允许
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_STATE_INVALID);
        }
        return buildResp(taskMapper.selectById(taskId), true, false);
    }

    @Override
    @AuditLog(action = "RETRY:AiTask")
    public AiTaskRespDTO retry(UUID taskId, LoginUser loginUser) {
        AiTask original = requireVisible(taskId, loginUser);
        if (!hasPermission(loginUser, PERMISSION_AI_TASK)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_NO_PERMISSION);
        }
        if (!Constants.AiTaskStatus.FAILED.equals(original.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_STATE_INVALID);
        }
        if (!settingsReader.settings().enabled()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_DISABLED);
        }
        if (handlerRegistry.get(original.getType()) == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_TYPE_UNSUPPORTED);
        }
        AiTask task = new AiTask();
        task.setType(original.getType());
        task.setStatus(Constants.AiTaskStatus.PENDING);
        task.setProgress(0);
        task.setProjectId(original.getProjectId());
        task.setWorkspaceId(original.getWorkspaceId());
        task.setSubmittedBy(loginUser.getId());
        task.setPromptScene(original.getPromptScene());
        // 复用原 model_id（3.6.4），用量仍可归因到原模型
        task.setModelId(original.getModelId());
        task.setInput(original.getInput() == null ? Map.of() : original.getInput());
        task.setTokensIn(0);
        task.setTokensOut(0);
        task.setRetryOfTaskId(original.getId());
        taskMapper.insert(task);
        startTask(task.getId());
        return buildResp(task, true, false);
    }

    @Override
    @AuditLog(action = "CONFIRM:AiArtifact")
    public AiTaskConfirmRespDTO confirm(UUID taskId, AiTaskConfirmReqDTO reqDTO, LoginUser loginUser) {
        AiTask task = requireVisible(taskId, loginUser);
        if (!hasPermission(loginUser, PERMISSION_AI_CONFIRM)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_NO_PERMISSION);
        }
        if (!Constants.AiTaskStatus.SUCCEEDED.equals(task.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_STATE_INVALID);
        }
        // 落库目标项目取自 X-Active-Project 头（C4），请求体只携带位置提示
        UUID projectId = loginUser.getActiveProjectId();
        UUID operatorId = loginUser.getId();
        List<ItemResult> results = new ArrayList<>();
        for (AiTaskConfirmReqDTO.ConfirmItemReqDTO item : reqDTO.getItems()) {
            results.add(confirmItem(task, item, reqDTO.getTarget(), projectId, operatorId));
        }
        AiTaskConfirmRespDTO resp = new AiTaskConfirmRespDTO();
        resp.setResults(results);
        return resp;
    }

    /**
     * 逐项确认（详设 3.6.5）：每项独立事务，采纳落库与确认记录同事务，失败仅该项回滚、其余项继续。
     */
    private ItemResult confirmItem(AiTask task, AiTaskConfirmReqDTO.ConfirmItemReqDTO item,
            AiTaskConfirmReqDTO.TargetReqDTO target, UUID projectId, UUID operatorId) {
        String action = item.getAction();
        try {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            return template.execute(status -> {
                Map<String, Object> artifact = findArtifact(task, item.getKey());
                if (artifact == null) {
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_ARTIFACT_NOT_FOUND);
                }
                if (action == null || !CONFIRM_ACTIONS.contains(action)) {
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
                }
                if (confirmMapper.selectByTaskAndKey(task.getId(), item.getKey()) != null) {
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_ARTIFACT_ALREADY_CONFIRMED);
                }
                ArtifactAdopter adopter = adopterRegistry.get(task.getType());
                if (adopter == null) {
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_TYPE_UNSUPPORTED);
                }
                AdoptOutcome outcome = adopter.adopt(new AdoptContext(task, artifact, action,
                        item.getContent(), item.getNote(),
                        target == null ? null : target.getModuleId(),
                        target == null ? null : target.getPosition(),
                        projectId, operatorId));
                AiArtifactConfirm record = new AiArtifactConfirm();
                record.setProjectId(projectId);
                record.setTaskId(task.getId());
                record.setArtifactKey(item.getKey());
                record.setAction(action);
                record.setOperatorId(operatorId);
                if (outcome != null) {
                    record.setAdoptedRef(outcome.adoptedRef());
                }
                record.setNote(item.getNote());
                // uk_ai_artifact_confirm 并发兜底：唯一冲突在本事务内转换为 1000018113
                confirmMapper.insert(record);
                return ItemResult.ok(item.getKey(), action, outcome == null ? null : outcome.createdId());
            });
        } catch (ServiceException e) {
            return ItemResult.fail(item.getKey(), action, e.getCode(), e.getMessage());
        } catch (DuplicateKeyException e) {
            return ItemResult.fail(item.getKey(), action,
                    ErrorCodeConstants.AI_ARTIFACT_ALREADY_CONFIRMED.code(),
                    ErrorCodeConstants.AI_ARTIFACT_ALREADY_CONFIRMED.msg());
        } catch (Exception e) {
            log.warn("[AI] 产物确认逐项失败 taskId={} key={}", task.getId(), item.getKey(), e);
            return ItemResult.fail(item.getKey(), action, ITEM_FAILED.code(), ITEM_FAILED.msg());
        }
    }

    /**
     * 起跑装配（详设 4.2）：直接调用覆盖「无事务」与「afterCommit 回调内」（行已提交）两类场景，
     * 注册兜底覆盖「活动事务内」场景（提交后触发）；执行器 casStart 条件写保证至多一次起跑，
     * 重复触发无副作用——嵌套 afterCommit（如影响分析链路）中注册的新同步不会被触发，故必须双保险。
     */
    private void startTask(UUID taskId) {
        taskExecutor.start(taskId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    taskExecutor.start(taskId);
                }
            });
        }
    }

    /**
     * 同步等待（3.6.2）：不改变异步语义，超时返回进行中状态转轮询。
     * 活动事务内插入行尚不可见，按进行中持续轮询；超时返回 null 由调用方回落插入时快照。
     */
    private AiTask await(UUID taskId, int waitSeconds) {
        long deadline = System.currentTimeMillis() + waitSeconds * 1000L;
        AiTask latest = null;
        while (true) {
            latest = taskMapper.selectById(taskId);
            if (latest != null && isTerminal(latest.getStatus())) {
                return latest;
            }
            if (System.currentTimeMillis() >= deadline) {
                return latest;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return latest;
            }
        }
    }

    /**
     * 可见性（详设 3.6.3）：带项目上下文看本项目任务，否则只看本人提交的任务；越权按不存在处理。
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

    private AiTaskRespDTO buildResp(AiTask task, boolean withArtifacts, boolean withResult) {
        AiTaskRespDTO resp = new AiTaskRespDTO();
        resp.setTaskId(task.getId());
        resp.setType(task.getType());
        resp.setStatus(task.getStatus());
        resp.setProgress(task.getProgress());
        resp.setPhase(task.getPhase());
        resp.setSubmittedBy(task.getSubmittedBy());
        resp.setRetryOfTaskId(task.getRetryOfTaskId());
        resp.setTokensIn(task.getTokensIn());
        resp.setTokensOut(task.getTokensOut());
        resp.setCreatedAt(task.getCreatedAt());
        if (Constants.AiTaskStatus.FAILED.equals(task.getStatus())) {
            resp.setError(new AiTaskRespDTO.TaskError(task.getErrorCode(), task.getErrorMsg()));
        }
        boolean succeeded = Constants.AiTaskStatus.SUCCEEDED.equals(task.getStatus());
        if (withResult && succeeded) {
            resp.setResult(task.getResult());
        }
        if (withArtifacts && succeeded) {
            resp.setArtifacts(artifactSummaries(task));
        }
        return resp;
    }

    /** 产物清单摘要（3.6.3）：confirmStatus 由 ai_artifact_confirm 回填，未确认为 pending */
    private List<AiTaskRespDTO.ArtifactSummary> artifactSummaries(AiTask task) {
        List<Map<String, Object>> artifacts = artifactsOf(task);
        if (artifacts.isEmpty()) {
            return List.of();
        }
        Map<String, String> confirmedActions = new LinkedHashMap<>();
        for (AiArtifactConfirm record : confirmMapper.selectByTask(task.getId())) {
            confirmedActions.putIfAbsent(record.getArtifactKey(), record.getAction());
        }
        List<AiTaskRespDTO.ArtifactSummary> summaries = new ArrayList<>();
        for (Map<String, Object> artifact : artifacts) {
            AiTaskRespDTO.ArtifactSummary summary = new AiTaskRespDTO.ArtifactSummary();
            summary.setKey(asString(artifact.get("key")));
            summary.setKind(asString(artifact.get("kind")));
            summary.setTitle(asString(artifact.get("title")));
            summary.setParentKey(asString(artifact.get("parentKey")));
            String key = summary.getKey();
            summary.setConfirmStatus(key != null && confirmedActions.containsKey(key)
                    ? confirmedActions.get(key) : "pending");
            summaries.add(summary);
        }
        return summaries;
    }

    private Map<String, Object> findArtifact(AiTask task, String artifactKey) {
        if (artifactKey == null) {
            return null;
        }
        return artifactsOf(task).stream()
                .filter(artifact -> artifactKey.equals(asString(artifact.get("key"))))
                .findFirst()
                .orElse(null);
    }

    /** result 为产物明细单一事实源（详设 2.6）；结构不符时按无产物处理 */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> artifactsOf(AiTask task) {
        if (task.getResult() == null || !(task.getResult().get("artifacts") instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(element -> element instanceof Map)
                .map(element -> (Map<String, Object>) element)
                .toList();
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean isTerminal(String status) {
        return Constants.AiTaskStatus.SUCCEEDED.equals(status)
                || Constants.AiTaskStatus.FAILED.equals(status)
                || Constants.AiTaskStatus.CANCELLED.equals(status);
    }

    private static boolean hasPermission(LoginUser loginUser, String permission) {
        return loginUser != null && loginUser.getPermissions().contains(permission);
    }
}
