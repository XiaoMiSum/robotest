package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiAnalysisTask;
import io.github.xiaomisum.robotest.repository.ai.AiAnalysisTaskMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.util.JsonUtils;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiTaskServiceImpl implements AiTaskService {

    private static final Set<String> IN_PROGRESS_STATUSES =
            Set.of(Constants.AiTaskStatus.PENDING, Constants.AiTaskStatus.RUNNING);

    @Resource
    private AiAnalysisTaskMapper taskMapper;
    /** 各任务类型处理器由对应业务模块交付时注册（当前允许为空，缺失时任务置 failed） */
    @Autowired(required = false)
    private List<AiTaskHandler> handlers = List.of();
    @Resource
    @Qualifier("aiTaskExecutor")
    private ThreadPoolTaskExecutor aiTaskExecutor;

    @Value("${server.port:8080}")
    private int serverPort;

    /** 执行实例标识：主机名:端口:启动UUID（多实例防重复消费） */
    private final String startupId = UUID.randomUUID().toString();

    private Map<String, AiTaskHandler> handlerMap;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiAnalysisTask createTask(String type, UUID workspaceId, UUID projectId, UUID targetId, UUID createdBy) {
        // SELECT … FOR UPDATE 锁定进行中同类记录，防并发双创建
        UUID lockProjectId = targetId == null ? projectId : null;
        List<AiAnalysisTask> inProgress = taskMapper.lockInProgress(type, targetId, lockProjectId);
        if (!inProgress.isEmpty()) {
            if (Constants.AiTaskType.EMBEDDING_REBUILD.equals(type)) {
                // 覆盖式创建：以最新配置为准，先取消进行中的旧重建任务
                inProgress.forEach(task -> taskMapper.markCancelledById(task.getId()));
            } else {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_DUPLICATE);
            }
        }

        AiAnalysisTask task = new AiAnalysisTask();
        task.setWorkspaceId(workspaceId);
        task.setProjectId(projectId);
        task.setType(type);
        task.setTargetId(targetId);
        task.setStatus(Constants.AiTaskStatus.PENDING);
        task.setProgress(0);
        task.setCreatedBy(createdBy);
        taskMapper.insert(task);
        submitAfterCommit(task.getId());
        return task;
    }

    @Override
    public AiTaskRespDTO getTask(UUID taskId, UUID projectId) {
        AiAnalysisTask task = taskMapper.selectById(taskId);
        if (task == null || task.getProjectId() == null || !task.getProjectId().equals(projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_NOT_FOUND);
        }
        return toRespDTO(task);
    }

    @Override
    public void cancelTask(UUID taskId, UUID userId) {
        AiAnalysisTask task = taskMapper.selectById(taskId);
        if (task == null || !IN_PROGRESS_STATUSES.contains(task.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_STATE_INVALID);
        }
        if (!task.getCreatedBy().equals(userId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }
        taskMapper.markCancelledById(taskId);
    }

    @Override
    public void retryTask(UUID taskId, UUID userId) {
        AiAnalysisTask task = taskMapper.selectById(taskId);
        if (task == null || !Constants.AiTaskStatus.FAILED.equals(task.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_STATE_INVALID);
        }
        if (!task.getCreatedBy().equals(userId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }
        if (!taskMapper.findInProgressByTypeAndTarget(task.getType(), task.getTargetId()).isEmpty()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_DUPLICATE);
        }
        taskMapper.resetToPending(taskId);
        trySubmit(taskId);
    }

    @Override
    public void cancelByTypeAndTarget(String type, UUID targetId) {
        taskMapper.cancelByTypeAndTarget(type, targetId);
    }

    @Override
    public void cancelAllInProgress() {
        taskMapper.cancelAllInProgress();
    }

    @Override
    public AiTaskRespDTO getLatestRebuildTask() {
        AiAnalysisTask task = taskMapper.findLatestByType(Constants.AiTaskType.EMBEDDING_REBUILD);
        return task == null ? null : toRespDTO(task);
    }

    @Override
    public void retryRebuildTask() {
        AiAnalysisTask task = taskMapper.findLatestByType(Constants.AiTaskType.EMBEDDING_REBUILD);
        if (task == null || !Set.of(Constants.AiTaskStatus.FAILED, Constants.AiTaskStatus.CANCELLED)
                .contains(task.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_STATE_INVALID);
        }
        taskMapper.resetToPending(task.getId());
        trySubmit(task.getId());
    }

    /**
     * 提交执行：队列满被 DiscardPolicy 丢弃时任务保持 pending，由 30 秒拾取定时任务兜底
     */
    public void trySubmit(UUID taskId) {
        aiTaskExecutor.execute(() -> executeTask(taskId));
    }

    /**
     * 任务执行主流程：乐观抢占 → 分发 handler → 终态落库
     */
    void executeTask(UUID taskId) {
        // 以 UPDATE … WHERE status='pending' 抢占，影响行数为 0 即被其他实例消费
        if (taskMapper.claimForExecution(taskId, instanceId()) == 0) {
            return;
        }
        AiAnalysisTask task = taskMapper.selectById(taskId);
        AiTaskHandler handler = handlerMap().get(task.getType());
        if (handler == null) {
            // handler 缺失置 failed 并给明确原因，保证状态机闭环（对应功能模块交付后注册）
            taskMapper.markFailedIfRunning(taskId, "任务类型 " + task.getType() + " 的执行器未实现");
            return;
        }
        try {
            Map<String, Object> result = handler.execute(task);
            taskMapper.markSuccessIfRunning(taskId, result != null ? JsonUtils.toJsonString(result) : null);
        } catch (Exception e) {
            log.warn("[AI] 任务执行失败 taskId={} type={}: {}", taskId, task.getType(), e.getMessage());
            taskMapper.markFailedIfRunning(taskId, truncate(e.getMessage()));
        }
    }

    /**
     * 事务提交后再提交线程池，避免执行线程读不到未提交的任务记录
     */
    private void submitAfterCommit(UUID taskId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    trySubmit(taskId);
                }
            });
        } else {
            trySubmit(taskId);
        }
    }

    private synchronized Map<String, AiTaskHandler> handlerMap() {
        if (handlerMap == null) {
            handlerMap = handlers.stream()
                    .collect(Collectors.toMap(AiTaskHandler::type, Function.identity(), (a, b) -> a));
        }
        return handlerMap;
    }

    private String instanceId() {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            hostname = "unknown";
        }
        String id = hostname + ":" + serverPort + ":" + startupId;
        return id.length() > 100 ? id.substring(0, 100) : id;
    }

    private String truncate(String message) {
        return message != null && message.length() > 500 ? message.substring(0, 500) : message;
    }

    private AiTaskRespDTO toRespDTO(AiAnalysisTask task) {
        AiTaskRespDTO dto = new AiTaskRespDTO();
        dto.setId(task.getId());
        dto.setType(task.getType());
        dto.setTargetId(task.getTargetId());
        dto.setStatus(task.getStatus());
        dto.setProgress(task.getProgress());
        dto.setResult(task.getResult());
        dto.setErrorMessage(task.getErrorMessage());
        dto.setCreatedBy(task.getCreatedBy());
        dto.setCreatedAt(task.getCreatedAt());
        dto.setUpdatedAt(task.getUpdatedAt());
        return dto;
    }
}
